package com.gitanalytics.digest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DigestPreferencesDto {
    private boolean digestEnabled;
    private int digestDayOfWeek;
    private int digestHour;
    private String timezone;
}
