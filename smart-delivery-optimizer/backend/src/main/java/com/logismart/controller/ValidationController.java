package com.logismart.controller;

import com.logismart.data.CsvParser;
import com.logismart.data.PlanningDataProvider;
import com.logismart.validation.DataValidator;
import com.logismart.validation.ValidationIssue;
import com.logismart.validation.ValidationReport;
import com.logismart.service.OptimizationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-quality endpoints.
 * <ul>
 *   <li>{@code GET  /api/validation/report} - validation of the loaded dataset
 *       (the same report the latest optimization run used)</li>
 *   <li>{@code POST /api/validation/orders} - validate an uploaded orders CSV
 *       against the current fleet and distance matrix (nothing is saved)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    /** Upload limit: generous for a 1,800-row dataset, small enough to protect the service. */
    private static final int MAX_UPLOAD_CHARS = 5_000_000;

    private final OptimizationService optimizationService;
    private final PlanningDataProvider provider;
    private final DataValidator validator;

    public ValidationController(
            OptimizationService optimizationService,
            PlanningDataProvider provider,
            DataValidator validator) {

        this.optimizationService = optimizationService;
        this.provider = provider;
        this.validator = validator;
    }

    @GetMapping("/report")
    public ValidationReport report() {
        return optimizationService.latest().validation();
    }

    @PostMapping(
            value = "/orders",
            consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public Map<String, Object> validateOrdersCsv(@RequestBody String csv) {

        if (csv.length() > MAX_UPLOAD_CHARS) {
            throw new IllegalArgumentException("CSV upload is larger than 5 MB");
        }

        CsvParser.OrderParseResult parsed = CsvParser.parseOrders(new StringReader(csv));

        DataValidator.Outcome outcome = validator.validate(
                parsed.orders(), provider.loadVehicles(), provider.loadDistances());

        // Only order findings are relevant to an orders upload.
        ValidationReport report = outcome.report();
        List<ValidationIssue> orderIssues = report.getIssues().stream()
                .filter(i -> i.entity().equals("ORDER"))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rowsRead", parsed.orders().size());
        body.put("validOrders", report.getValidOrders());
        body.put("invalidOrders", report.getInvalidOrders());
        body.put("rowProblems", parsed.rowProblems());
        body.put("errorCount", orderIssues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count());
        body.put("warningCount", orderIssues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count());
        body.put("issueCountsByRule", orderIssues.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ValidationIssue::rule, java.util.TreeMap::new,
                        java.util.stream.Collectors.counting())));
        body.put("issues", orderIssues);
        return body;
    }
}
