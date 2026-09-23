package com.otboo.weather.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.common.exception.BusinessException;
import com.otboo.weather.WeatherGridConverter;
import com.otboo.weather.exception.WeatherErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "weather_regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherRegion extends BaseEntity {

    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location_names", nullable = false, columnDefinition = "json")
    private List<String> locationNames;

    private WeatherRegion(int gridX, int gridY, List<String> locationNames) {
        WeatherGridConverter.validateGrid(gridX, gridY);
        if (locationNames == null || locationNames.isEmpty()
                || locationNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new BusinessException(WeatherErrorCode.INVALID_LOCATION_NAMES);
        }
        this.gridX = gridX;
        this.gridY = gridY;
        this.locationNames = List.copyOf(locationNames);
    }

    public static WeatherRegion create(int gridX, int gridY, List<String> locationNames) {
        return new WeatherRegion(gridX, gridY, locationNames);
    }

    public List<String> getLocationNames() {
        return List.copyOf(locationNames);
    }
}
