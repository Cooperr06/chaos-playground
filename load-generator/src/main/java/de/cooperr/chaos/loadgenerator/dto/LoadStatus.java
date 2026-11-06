package de.cooperr.chaos.loadgenerator.dto;

import java.util.List;

public record LoadStatus(int ratePerSecond, List<TargetStatus> targets) {
}
