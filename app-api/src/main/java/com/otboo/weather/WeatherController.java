package com.otboo.weather;

import com.otboo.weather.dto.WeatherDto;
import com.otboo.weather.dto.WeatherApiLocation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weathers")
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    public List<WeatherDto> find(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return weatherService.find(latitude, longitude);
    }

    @GetMapping("/location")
    public WeatherApiLocation findLocation(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return weatherService.findLocation(latitude, longitude);
    }
}
