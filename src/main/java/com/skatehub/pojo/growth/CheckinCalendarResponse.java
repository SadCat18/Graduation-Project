package com.skatehub.pojo.growth;

import java.time.LocalDate;
import java.util.List;

public record CheckinCalendarResponse(String month, List<LocalDate> checkedDates) {
}
