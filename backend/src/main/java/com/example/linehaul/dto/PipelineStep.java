package com.example.linehaul.dto;

/** One stage of the assignment pipeline, shown as a check list in the UI. */
public class PipelineStep {

    /** PASSED, FAILED, WARNING or INFO. */
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";

    private String label;
    private String status;
    private String detail;

    public PipelineStep() {
    }

    public PipelineStep(String label, String status, String detail) {
        this.label = label;
        this.status = status;
        this.detail = detail;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
