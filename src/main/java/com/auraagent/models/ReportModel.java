package com.auraagent.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportModel {

    @JsonProperty("campaign_id")
    private String campaignId = "";

    @JsonProperty("date")
    private String date = "";

    @JsonProperty("total_contacts")
    private int totalContacts;

    @JsonProperty("success_count")
    private int successCount;

    @JsonProperty("fail_count")
    private int failCount;

    // Getters and Setters
    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getTotalContacts() {
        return totalContacts;
    }

    public void setTotalContacts(int totalContacts) {
        this.totalContacts = totalContacts;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }
}