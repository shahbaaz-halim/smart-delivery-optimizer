package com.logismart.algorithm;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Optimizer v2: a multi-day, multi-trip cheapest-insertion heuristic with
 * time windows, followed by 2-opt improvement.
 *
 * <h2>Model</h2>
 * Objective: minimise total operating cost (or distance) of all trips.
 * <br>Hard constraints:
 * <ul>
 *   <li>a vehicle only serves orders from its own hub and must be Available</li>
 *   <li>vehicle class &ge; required class (exact match if upgrades are off)</li>
 *   <li>trip load &le; vehicle capacity (the vehicle reloads at the hub between trips)</li>
 *   <li>all trips of a vehicle-day fit inside its shift
 *       (day start + Max_Working_Minutes), including reload time</li>
 *   <li>service at each stop starts inside the order's delivery window
 *       (when {@code enforceTimeWindows} is on)</li>
 * </ul>
 *
 * <h2>Algorithm (per planning day)</h2>
 * <ol>
 *   <li>Put remaining orders in a {@link PriorityQueue}: priority, then
 *       earliest window end, then zone (so same-zone orders are consecutive),
 *       then heavier first.</li>
 *   <li>For each order, evaluate every feasible option and take the cheapest:
 *       insert it at any position of a compatible vehicle's open trip, or
 *       start a new trip (on an idle vehicle, or on a busy vehicle after it
 *       returns and reloads).</li>
 *   <li>Orders with no feasible option today are deferred to the next day.</li>
 *   <li>Apply 2-opt to every trip, keeping only feasible, shorter sequences.</li>
 * </ol>
 *
 * <p>Complexity per day: O(N * V * S^2), where N = queued orders,
 * V = vehicles at the order's hub and S = stops per trip; schedule
 * simulation is O(S). With N = 1,800, V &le; 13 and S &asymp; 5, a full run
 * takes well under a second.
 */
@Component
public class RouteOptimizer {

    private static final double EPS = 1e-9;

    public OptimizationResult optimize(
            List<DeliveryOrder> orders,
            List<Vehicle> vehicles,
            DistanceMatrix matrix,
            OptimizationParameters params) {

        long started = System.nanoTime();

        Context ctx = new Context(matrix, params, vehicles);

        List<DeliveryOrder> remaining = new ArrayList<>();
        List<DeliveryOrder> unallocated = new ArrayList<>();

        // Remove orders that can never be served, with an explanation.
        for (DeliveryOrder order : orders) {

            String reason = ctx.structuralInfeasibility(order);

            if (reason == null) {
                remaining.add(order);
            } else {
                order.setAllocationReason(reason);
                unallocated.add(order);
            }
        }

        List<RoutePlan> allRoutes = new ArrayList<>();
        int twoOptMoves = 0;
        double twoOptSaved = 0.0;

        for (int day = 1;
             day <= params.getMaxPlanningDays() && !remaining.isEmpty();
             day++) {

            DayPlan plan = new DayPlan(ctx, day);

            PriorityQueue<DeliveryOrder> queue =
                    new PriorityQueue<>(ctx.dispatchOrder());
            queue.addAll(remaining);

            List<DeliveryOrder> deferred = new ArrayList<>();

            while (!queue.isEmpty()) {

                DeliveryOrder order = queue.poll();

                if (!plan.assign(order)) {
                    deferred.add(order);
                }
            }

            if (deferred.size() == remaining.size()) {
                // Nothing could be scheduled - more days will not help.
                break;
            }

            if (params.isTwoOpt()) {
                for (Trip trip : plan.allTrips()) {
                    double before = trip.distanceKm;
                    int moves = plan.twoOpt(trip);
                    if (moves > 0) {
                        twoOptMoves += moves;
                        twoOptSaved += before - trip.distanceKm;
                    }
                }
            }

            allRoutes.addAll(plan.toRoutePlans());
            remaining = deferred;
        }

        for (DeliveryOrder order : remaining) {
            order.setAllocationReason(
                    "Not scheduled within the " + params.getMaxPlanningDays()
                    + "-day planning horizon");
            unallocated.add(order);
        }

        long runtimeMs = (System.nanoTime() - started) / 1_000_000;

        return new OptimizationResult(
                allRoutes,
                unallocated,
                twoOptMoves,
                twoOptSaved,
                runtimeMs);
    }

    // ------------------------------------------------------------------
    // Shared, read-only context for one optimization run
    // ------------------------------------------------------------------

    static final class Context {

        final DistanceMatrix matrix;
        final OptimizationParameters params;
        final int dayStart;

        /** Available vehicles grouped by hub - O(1) candidate lookup. */
        final Map<String, List<Vehicle>> vehiclesByHub = new HashMap<>();

        private final Map<String, TimeWindow> windowCache = new HashMap<>();

        Context(DistanceMatrix matrix,
                OptimizationParameters params,
                List<Vehicle> vehicles) {

            this.matrix = matrix;
            this.params = params;
            this.dayStart = params.dayStartMinutes();

            List<Vehicle> sorted = new ArrayList<>(vehicles);
            sorted.sort(Comparator.comparing(
                    v -> v.getVehicleId() == null ? "" : v.getVehicleId()));

            for (Vehicle v : sorted) {
                if (isUsable(v)) {
                    vehiclesByHub
                            .computeIfAbsent(key(v.getHub()), k -> new ArrayList<>())
                            .add(v);
                }
            }
        }

        static boolean isUsable(Vehicle v) {
            return v != null
                    && v.getAvailability() != null
                    && v.getAvailability().trim().equalsIgnoreCase("Available")
                    && v.getHub() != null
                    && VehicleClass.parse(v.getVehicleType()) != null
                    && v.getCapacityKg() > 0
                    && v.getMaxWorkingMinutes() > 0;
        }

        static String key(String s) {
            return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        }

        TimeWindow window(DeliveryOrder o) {
            String raw = o.getDeliveryWindow() == null ? "" : o.getDeliveryWindow();
            return windowCache.computeIfAbsent(raw, TimeWindow::parse);
        }

        boolean classCompatible(DeliveryOrder o, Vehicle v) {

            VehicleClass required = VehicleClass.parse(o.getRequiredVehicleType());
            VehicleClass actual = VehicleClass.parse(v.getVehicleType());

            if (required == null || actual == null) {
                return false;
            }

            return params.isAllowVehicleUpgrade()
                    ? actual.canServe(required)
                    : actual == required;
        }

        List<Vehicle> candidates(DeliveryOrder o) {

            List<Vehicle> result = new ArrayList<>();

            for (Vehicle v : vehiclesByHub.getOrDefault(key(o.getOriginHub()), List.of())) {
                if (classCompatible(o, v)
                        && o.getPackageWeightKg() <= v.getCapacityKg() + EPS) {
                    result.add(v);
                }
            }

            return result;
        }

        /** Null when the order could be served by at least one empty vehicle-day. */
        String structuralInfeasibility(DeliveryOrder o) {

            List<Vehicle> atHub = vehiclesByHub.getOrDefault(key(o.getOriginHub()), List.of());

            if (atHub.isEmpty()) {
                return "No available vehicle at origin hub";
            }

            if (window(o) == null) {
                return "Invalid delivery window";
            }

            if (!matrix.isReachable(o.getOriginHub(), o.getDestinationZone())
                    || !matrix.isReachable(o.getDestinationZone(), o.getOriginHub())) {
                return "Destination zone not reachable in distance network";
            }

            boolean typeMatch = atHub.stream().anyMatch(v -> classCompatible(o, v));

            if (!typeMatch) {
                return params.isAllowVehicleUpgrade()
                        ? "No available vehicle of the required or a larger class at origin hub"
                        : "No available vehicle of the required type at origin hub";
            }

            List<Vehicle> fits = candidates(o);

            if (fits.isEmpty()) {
                return "Package weight exceeds capacity of every suitable vehicle";
            }

            for (Vehicle v : fits) {
                Schedule s = simulate(v, List.of(o), dayStart, shiftEnd(v));
                if (s.feasible) {
                    return null;
                }
            }

            return "Cannot be delivered within delivery window and working-time limits";
        }

        double shiftEnd(Vehicle v) {
            return dayStart + v.getMaxWorkingMinutes();
        }

        double minutes(double km) {
            return km / params.getAverageSpeedKmph() * 60.0;
        }

        /**
         * Simulates a trip and returns its schedule. O(S).
         * Working-time (shift) limits are always hard; windows are hard only
         * when {@code enforceTimeWindows} is on (otherwise lateness is recorded).
         */
        Schedule simulate(
                Vehicle v,
                List<DeliveryOrder> seq,
                double start,
                double shiftEnd) {

            Schedule s = new Schedule();
            s.start = start;

            double t = start;
            String loc = v.getHub();

            for (DeliveryOrder o : seq) {

                double km = matrix.distance(loc, o.getDestinationZone());

                if (Double.isInfinite(km)) {
                    s.feasible = false;
                    return s;
                }

                double travel = minutes(km);
                s.distanceKm += km;
                s.travelMin += travel;
                t += travel;

                double arrival = t;

                TimeWindow tw = window(o);
                double winStart = tw == null || tw.isFlexible() ? dayStart : tw.startMin();
                double winEnd = tw == null || tw.isFlexible() ? shiftEnd : tw.endMin();

                if (t < winStart) {
                    s.waitMin += winStart - t;
                    t = winStart;
                }

                double late = Math.max(0.0, t - winEnd);

                if (late > EPS) {
                    s.lateMin += late;
                    s.lateStops++;
                    if (params.isEnforceTimeWindows()) {
                        s.feasible = false;
                    }
                }

                StopSchedule stop = new StopSchedule();
                stop.setOrderId(o.getOrderId());
                stop.setDestinationZone(o.getDestinationZone());
                stop.setSequence(s.stops.size() + 1);
                stop.setArrivalMin(arrival);
                stop.setServiceStartMin(t);
                stop.setWindowStartMin(winStart);
                stop.setWindowEndMin(winEnd);
                stop.setWaitMin(Math.max(0.0, t - arrival));
                stop.setLateMin(late);

                t += o.getServiceTimeMin();
                s.serviceMin += o.getServiceTimeMin();
                stop.setDepartureMin(t);

                s.stops.add(stop);
                s.weightKg += o.getPackageWeightKg();
                loc = o.getDestinationZone();
            }

            double back = matrix.distance(loc, v.getHub());

            if (Double.isInfinite(back)) {
                s.feasible = false;
                return s;
            }

            s.distanceKm += back;
            s.travelMin += minutes(back);
            t += minutes(back);
            s.end = t;

            if (s.end > shiftEnd + EPS) {
                s.feasible = false;
            }

            if (s.weightKg > v.getCapacityKg() + EPS) {
                s.feasible = false;
            }

            return s;
        }

        double score(Vehicle v, double deltaKm) {
            return params.getObjective() == OptimizationParameters.Objective.COST
                    ? deltaKm * v.getCostPerKmInr()
                    : deltaKm;
        }

        int classGap(DeliveryOrder o, Vehicle v) {
            VehicleClass required = VehicleClass.parse(o.getRequiredVehicleType());
            VehicleClass actual = VehicleClass.parse(v.getVehicleType());
            return required == null || actual == null ? 0 : actual.ordinal() - required.ordinal();
        }

        Comparator<DeliveryOrder> dispatchOrder() {
            return Comparator
                    .comparingInt((DeliveryOrder o) -> priorityRank(o.getPriority()))
                    .thenComparingInt(o -> windowEnd(o))
                    .thenComparingInt(o -> windowStart(o))
                    .thenComparing(o -> key(o.getDestinationZone()))
                    .thenComparing(DeliveryOrder::getPackageWeightKg, Comparator.reverseOrder())
                    .thenComparing(o -> o.getOrderId() == null ? "" : o.getOrderId());
        }

        private int windowEnd(DeliveryOrder o) {
            TimeWindow tw = window(o);
            return tw == null || tw.isFlexible() ? Integer.MAX_VALUE : tw.endMin();
        }

        private int windowStart(DeliveryOrder o) {
            TimeWindow tw = window(o);
            return tw == null || tw.isFlexible() ? Integer.MAX_VALUE : tw.startMin();
        }

        static int priorityRank(String priority) {
            if (priority == null) {
                return 4;
            }
            return switch (priority.trim().toLowerCase(Locale.ROOT)) {
                case "high" -> 1;
                case "medium" -> 2;
                case "low" -> 3;
                default -> 4;
            };
        }
    }

    static final class Schedule {
        boolean feasible = true;
        double start;
        double end;
        double distanceKm;
        double travelMin;
        double serviceMin;
        double waitMin;
        double lateMin;
        int lateStops;
        double weightKg;
        List<StopSchedule> stops = new ArrayList<>();
    }

    static final class Trip {
        final Vehicle vehicle;
        final int tripNumber;
        final List<DeliveryOrder> orders = new ArrayList<>();
        Schedule schedule;
        double distanceKm;

        Trip(Vehicle vehicle, int tripNumber) {
            this.vehicle = vehicle;
            this.tripNumber = tripNumber;
        }
    }

    /** One candidate move evaluated for an order. */
    private record Option(
            Vehicle vehicle,
            Trip trip,        // null => open a new trip
            int position,
            double start,
            double score,
            int classGap,
            Schedule schedule) {
    }

    private static final Comparator<Option> BEST_OPTION =
            Comparator.comparingDouble(Option::score)
                    .thenComparingInt(Option::classGap)
                    .thenComparingDouble(o -> o.schedule().end);

    // ------------------------------------------------------------------
    // One planning day
    // ------------------------------------------------------------------

    static final class DayPlan {

        private final Context ctx;
        private final int day;

        /** Trips per vehicle in chronological order; the last one is "open". */
        private final Map<String, List<Trip>> tripsByVehicle = new LinkedHashMap<>();

        DayPlan(Context ctx, int day) {
            this.ctx = ctx;
            this.day = day;
        }

        boolean assign(DeliveryOrder order) {

            Option best = null;

            for (Vehicle v : ctx.candidates(order)) {

                double shiftEnd = ctx.shiftEnd(v);
                List<Trip> trips = tripsByVehicle.getOrDefault(v.getVehicleId(), List.of());
                Trip open = trips.isEmpty() ? null : trips.get(trips.size() - 1);

                // (a) insert into the open trip at every position
                if (open != null
                        && open.schedule.weightKg + order.getPackageWeightKg()
                           <= v.getCapacityKg() + EPS) {

                    for (int pos = 0; pos <= open.orders.size(); pos++) {

                        List<DeliveryOrder> seq = new ArrayList<>(open.orders);
                        seq.add(pos, order);

                        Schedule s = ctx.simulate(v, seq, open.schedule.start, shiftEnd);

                        if (!s.feasible) {
                            continue;
                        }

                        Option opt = new Option(
                                v, open, pos, open.schedule.start,
                                ctx.score(v, s.distanceKm - open.distanceKm),
                                ctx.classGap(order, v), s);

                        if (best == null || BEST_OPTION.compare(opt, best) < 0) {
                            best = opt;
                        }
                    }
                }

                // (b) start a new trip
                double start;

                if (open == null) {
                    start = ctx.dayStart;
                } else if (ctx.params.isMultiTrip()) {
                    start = open.schedule.end + ctx.params.getReloadTimeMin();
                } else {
                    continue;
                }

                Schedule s = ctx.simulate(v, List.of(order), start, shiftEnd);

                if (s.feasible) {
                    Option opt = new Option(
                            v, null, 0, start,
                            ctx.score(v, s.distanceKm),
                            ctx.classGap(order, v), s);

                    if (best == null || BEST_OPTION.compare(opt, best) < 0) {
                        best = opt;
                    }
                }
            }

            if (best == null) {
                return false;
            }

            if (best.trip() != null) {
                best.trip().orders.add(best.position(), order);
                apply(best.trip(), best.schedule());
            } else {
                List<Trip> trips = tripsByVehicle.computeIfAbsent(
                        best.vehicle().getVehicleId(), k -> new ArrayList<>());
                Trip trip = new Trip(best.vehicle(), trips.size() + 1);
                trip.orders.add(order);
                apply(trip, best.schedule());
                trips.add(trip);
            }

            return true;
        }

        private static void apply(Trip trip, Schedule s) {
            trip.schedule = s;
            trip.distanceKm = s.distanceKm;
        }

        List<Trip> allTrips() {
            List<Trip> all = new ArrayList<>();
            tripsByVehicle.values().forEach(all::addAll);
            return all;
        }

        /**
         * 2-opt: reverse every sub-sequence [i..j]; keep the reversal when
         * the trip gets shorter, stays feasible and does not finish later
         * (so the vehicle's following trip is unaffected). Repeats until no
         * improving move exists. Returns the number of accepted moves.
         */
        int twoOpt(Trip trip) {

            int moves = 0;
            boolean improved = true;
            double shiftEnd = ctx.shiftEnd(trip.vehicle);

            while (improved && trip.orders.size() >= 2) {

                improved = false;

                for (int i = 0; i < trip.orders.size() - 1 && !improved; i++) {
                    for (int j = i + 1; j < trip.orders.size() && !improved; j++) {

                        List<DeliveryOrder> seq = new ArrayList<>(trip.orders);
                        Collections.reverse(seq.subList(i, j + 1));

                        Schedule s = ctx.simulate(
                                trip.vehicle, seq, trip.schedule.start, shiftEnd);

                        if (s.feasible
                                && s.distanceKm < trip.distanceKm - 1e-6
                                && s.end <= trip.schedule.end + EPS) {

                            trip.orders.clear();
                            trip.orders.addAll(seq);
                            apply(trip, s);
                            moves++;
                            improved = true;
                        }
                    }
                }
            }

            return moves;
        }

        List<RoutePlan> toRoutePlans() {

            List<RoutePlan> plans = new ArrayList<>();

            for (Trip trip : allTrips()) {

                Schedule s = trip.schedule;
                RoutePlan plan = new RoutePlan();

                plan.setVehicle(trip.vehicle);
                plan.setPlanningDay(day);
                plan.setTripNumber(trip.tripNumber);
                plan.setOrders(new ArrayList<>(trip.orders));
                plan.setStops(s.stops);
                plan.setStartMin(s.start);
                plan.setEndMin(s.end);
                plan.setTotalDistanceKm(s.distanceKm);
                plan.setTotalWeightKg(s.weightKg);
                plan.setTotalTimeMin(s.end - s.start);
                plan.setTravelTimeMin(s.travelMin);
                plan.setServiceTimeMin(s.serviceMin);
                plan.setWaitTimeMin(s.waitMin);
                plan.setTotalCostInr(s.distanceKm * trip.vehicle.getCostPerKmInr());

                plans.add(plan);
            }

            plans.sort(Comparator
                    .comparing((RoutePlan p) -> p.getVehicle().getVehicleId())
                    .thenComparingInt(RoutePlan::getTripNumber));

            return plans;
        }
    }
}
