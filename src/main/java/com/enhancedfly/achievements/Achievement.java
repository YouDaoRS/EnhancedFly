package com.enhancedfly.achievements;

public class Achievement {
    private final String id;
    private final String name;
    private final String description;
    private final String icon;
    private final AchievementType type;
    private final double requiredValue;

    public Achievement(String id, String name, String description, String icon, AchievementType type) {
        this(id, name, description, icon, type, 0);
    }

    public Achievement(String id, String name, String description, String icon, AchievementType type, double requiredValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.type = type;
        this.requiredValue = requiredValue;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public AchievementType getType() {
        return type;
    }

    public double getRequiredValue() {
        return requiredValue;
    }
}
