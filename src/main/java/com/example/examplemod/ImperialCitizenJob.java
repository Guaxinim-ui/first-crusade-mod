package com.example.examplemod;

public enum ImperialCitizenJob {
    UNEMPLOYED("Unemployed"),
    MINER("Miner"),
    SCRAPPER("Scrapper"),
    SMITH("Smith"),
    FARMER("Farmer"),
    BUILDER("Builder"),
    RECRUIT("Recruit");

    private final String displayName;

    ImperialCitizenJob(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}