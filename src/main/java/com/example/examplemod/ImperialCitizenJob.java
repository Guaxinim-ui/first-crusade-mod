package com.example.examplemod;

public enum ImperialCitizenJob {
    UNEMPLOYED("Unemployed"),
    MINER("Miner"),
    GOLD_MINER("Gold Miner"),
    SCRAPPER("Scrapper"),
    SMITH("Smith"),
    STOKER("Stoker"),
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