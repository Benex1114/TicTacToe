package com.example.tictactoe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class MakeMoveRequest {

    @NotNull(message = "cellIndex is required")
    @Min(value = 0, message = "cellIndex must be between 0 and 8")
    @Max(value = 8, message = "cellIndex must be between 0 and 8")
    private Integer cellIndex;

    public Integer getCellIndex() {
        return cellIndex;
    }

    public void setCellIndex(Integer cellIndex) {
        this.cellIndex = cellIndex;
    }
}