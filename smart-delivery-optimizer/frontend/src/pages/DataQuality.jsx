import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

/*
 * Data ingestion & validation screen:
 *  1. Validation report for the dataset currently loaded by the backend.
 *  2. Upload an orders CSV to validate it against the fleet and distance
 *     matrix (nothing is saved).
 */

const MAX_ROWS_SHOWN = 200;

function IssueTable({ issues }) {
  const [severity, setSeverity] = useState("ALL");
  const [rule, setRule] = useState("ALL");

  const rules = useMemo(
    () => [...new Set(issues.map((i) => i.rule))].sort(),
    [issues]
  );

  const filtered = issues.filter(
    (i) =>
      (severity === "ALL" || i.severity === severity) &&
      (rule === "ALL" || i.rule === rule)
  );

  return (
    <>
      <div className="trips-toolbar">
        <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
          <option value="ALL">All severities</option>
          <option value="ERROR">Errors (record excluded)</option>
          <option value="WARNING">Warnings (record kept)</option>
        </select>
        <select value={rule} onChange={(e) => setRule(e.target.value)}>
          <option value="ALL">All rules</option>
          {rules.map((r) => (
            <option key={r} value={r}>{r}</option>
          ))}
        </select>
        <span className="form-hint">
          Showing {Math.min(filtered.length, MAX_ROWS_SHOWN)} of {filtered.length}
        </span>
      </div>

      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Severity</th>
              <th>Entity</th>
              <th>Record</th>
              <th>Field</th>
              <th>Rule</th>
              <th>Value</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan="7" className="empty-table">No issues found.</td>
              </tr>
            )}
            {filtered.slice(0, MAX_ROWS_SHOWN).map((i, idx) => (
              <tr key={`${i.recordId}-${i.rule}-${idx}`}>
                <td>
                  <span className={`severity-badge ${i.severity.toLowerCase()}`}>{i.severity}</span>
                </td>
                <td>{i.entity}</td>
                <td className="order-id-cell">{i.recordId}</td>
                <td>{i.field}</td>
                <td>{i.rule}</td>
                <td>{i.value}</td>
                <td>{i.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function RuleCounts({ counts }) {
  const entries = Object.entries(counts || {});
  if (entries.length === 0) {
    return null;
  }
  return (
    <div className="rule-chips">
      {entries.map(([rule, n]) => (
        <span key={rule} className="status-badge">
          {rule}: <strong>{n}</strong>
        </span>
      ))}
    </div>
  );
}

function DataQuality() {
  const [report, setReport] = useState(null);
  const [error, setError] = useState("");

  const [fileName, setFileName] = useState("");
  const [upload, setUpload] = useState(null);
  const [uploadError, setUploadError] = useState("");
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    api.getValidationReport().then(setReport).catch((e) => setError(e.message));
  }, []);

  async function handleFile(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }

    setFileName(file.name);
    setUpload(null);
    setUploadError("");

    if (file.size > 5 * 1024 * 1024) {
      setUploadError("File is larger than 5 MB.");
      return;
    }

    setUploading(true);
    try {
      setUpload(await api.validateOrdersCsv(await file.text()));
    } catch (e) {
      setUploadError(e.message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Data Quality</h1>
          <p>Ingestion checks run before every optimization. Records with errors are excluded and reported.</p>
        </div>
        {report && (
          <span className={`health-badge ${report.valid ? "green" : "red"}`}>
            <span className="health-dot" />
            {report.valid ? "Dataset valid" : `${report.errorCount} errors`}
          </span>
        )}
      </div>

      {error && (
        <div className="error-message">
          <strong>Unable to load the validation report.</strong>
          <p>{error}</p>
        </div>
      )}

      {report && (
        <section className="dashboard-section">
          <div className="section-heading">
            <h2>Loaded Dataset</h2>
            <p>{report.errorCount} errors · {report.warningCount} warnings</p>
          </div>

          <div className="summary-box-grid">
            <div className="summary-box">
              <div className="summary-box-label">Orders valid</div>
              <div className="summary-box-value">{report.validOrders} / {report.totalOrders}</div>
              <div className="summary-box-meta">{report.invalidOrders} excluded</div>
            </div>
            <div className="summary-box">
              <div className="summary-box-label">Vehicles valid</div>
              <div className="summary-box-value">{report.validVehicles} / {report.totalVehicles}</div>
              <div className="summary-box-meta">Maintenance vehicles are valid but not dispatched</div>
            </div>
            <div className="summary-box">
              <div className="summary-box-label">Distance records valid</div>
              <div className="summary-box-value">
                {report.validDistanceRecords} / {report.totalDistanceRecords}
              </div>
              <div className="summary-box-meta">Zone matrix cells</div>
            </div>
          </div>

          <RuleCounts counts={report.issueCountsByRule} />
          <IssueTable issues={report.issues} />
        </section>
      )}

      <section className="dashboard-section">
        <div className="section-heading">
          <h2>Validate an Orders File</h2>
          <p>CSV with the Delivery Orders columns · validated against the current fleet and zone matrix · nothing is saved</p>
        </div>

        <div className="dashboard-panel">
          <div className="form-actions">
            <label className="primary-button file-button">
              {uploading ? "Validating..." : "Choose CSV file"}
              <input type="file" accept=".csv,text/csv" onChange={handleFile} disabled={uploading} hidden />
            </label>
            <span className="form-hint">
              {fileName || "Try database/test-data/invalid_orders_sample.csv from the repository."}
            </span>
          </div>

          {uploadError && (
            <div className="error-message">
              <strong>File rejected.</strong>
              <p>{uploadError}</p>
            </div>
          )}

          {upload && (
            <>
              <div className="summary-box-grid">
                <div className="summary-box">
                  <div className="summary-box-label">Rows read</div>
                  <div className="summary-box-value">{upload.rowsRead}</div>
                </div>
                <div className="summary-box">
                  <div className="summary-box-label">Valid orders</div>
                  <div className="summary-box-value">{upload.validOrders}</div>
                </div>
                <div className="summary-box">
                  <div className="summary-box-label">Invalid orders</div>
                  <div className="summary-box-value">{upload.invalidOrders}</div>
                </div>
                <div className="summary-box">
                  <div className="summary-box-label">Errors / warnings</div>
                  <div className="summary-box-value">
                    {upload.errorCount} / {upload.warningCount}
                  </div>
                </div>
              </div>

              {upload.rowProblems?.length > 0 && (
                <div className="error-message">
                  <strong>Structural problems</strong>
                  {upload.rowProblems.slice(0, 20).map((p) => (
                    <p key={p}>{p}</p>
                  ))}
                </div>
              )}

              <RuleCounts counts={upload.issueCountsByRule} />
              <IssueTable issues={upload.issues} />
            </>
          )}
        </div>
      </section>
    </div>
  );
}

export default DataQuality;
