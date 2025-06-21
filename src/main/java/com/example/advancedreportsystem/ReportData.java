package com.example.advancedreportsystem;

public class ReportData {
    private final String reason;
    private String targetName;
    private String additionalInfo;

    public ReportData(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }
}