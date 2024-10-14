package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.TugDTO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LogPatternMatcher {

    private LogPatternMatcher() {
        throw new IllegalStateException("Utility class");
    }

    // Regex to match the ModelTrackDTO and nested TugDTO fields
//   19-09-2024 17:24:45.570 - Translated message: ModelTrackDTO(modelName=6, positionX=55.96, positionY=63.71, speed=0.0, heading=229.8, rudder=0.0, gpsQuality=1.59, engine=100.0, bowThruster=-127.0, bowTug=TugDTO(tugForce=10.0, tugDirection=0.0), sternTug=TugDTO(tugForce=10.0, tugDirection=469.45))
    private final static String REGEX_LADY_MARIE = "(\\d{2}-\\d{2}-\\d{4})\\s(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s-\\sTranslated message: ModelTrackDTO\\(modelName=(\\d+),\\spositionX=([-+]?\\d*\\.?\\d+),\\spositionY=([-+]?\\d*\\.?\\d+),\\sspeed=([\\d\\.]+),\\sheading=([\\d\\.]+),\\srudder=([\\d\\.]+),\\sgpsQuality=([\\d\\.]+),\\sengine=([\\d\\.]+),\\sbowThruster=([\\-\\d\\.]+),\\sbowTug=TugDTO\\(tugForce=([\\d\\.]+),\\stugDirection=([\\d\\.]+)\\),\\ssternTug=TugDTO\\(tugForce=([\\d\\.]+),\\stugDirection=([\\d\\.]+)\\)\\)";

//  19-09-2024 14:10:37.358 - Translated message: ModelTrackDTO(modelName=1, positionX=71.14, positionY=59.78, speed=0.1, heading=232.5, rudder=-12.3, gpsQuality=0.03, engine=6.0, bowThruster=111.0, bowTug=TugDTO(tugForce=0.0, tugDirection=22.35), sternTug=TugDTO(tugForce=0.0, tugDirection=22.35))
//  07-10-2024 10:10:34.871 - Translated message: ModelTrackDTO(modelName=1, positionX=-208.19, positionY=185.31, speed=9.3, heading=105.9, rudder=2.5, gpsQuality=0.03, engine=7.0, bowThruster=111.0, bowTug=TugDTO(tugForce=0.0, tugDirection=9.69), sternTug=TugDTO(tugForce=0.0, tugDirection=9.69))
    private final static String REGEX_OTHER_MODELS = "(\\d{2}-\\d{2}-\\d{4})\\s(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s-\\sTranslated message: ModelTrackDTO\\(modelName=(\\d+), positionX=([-+]?\\d*\\.?\\d+), positionY=([-+]?\\d*\\.?\\d+), speed=([\\d.]+), heading=([\\d.]+), rudder=(-?[\\d.]+), gpsQuality=([\\d.]+), engine=([\\d.]+), bowThruster=([\\d.]+), bowTug=TugDTO\\(tugForce=([\\d.]+), tugDirection=([\\d.]+)\\), sternTug=TugDTO\\(tugForce=([\\d.]+), tugDirection=([\\d.]+)\\)\\)";


    private static Matcher getMatcher(String regex, String input) throws PatternSyntaxException {
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(input);
    }

    public static Optional<LogItem> parseTrack(String input) throws IllegalArgumentException {
        Matcher matcherLadyMarie = getMatcher(REGEX_LADY_MARIE, input);
        Matcher matcherOtherModels = getMatcher(REGEX_OTHER_MODELS, input);
        if (matcherLadyMarie.find()) {
            if (isValidModelId(matcherLadyMarie)) {
                return Optional.empty();
            }
            // Parsing date and time
            String date = matcherLadyMarie.group(1);
            String time = matcherLadyMarie.group(2);

            // Parsing TugDTO objects
            TugDTO bowTug = new TugDTO(Double.parseDouble(matcherLadyMarie.group(12)), Double.parseDouble(matcherLadyMarie.group(13)));
            TugDTO sternTug = new TugDTO(Double.parseDouble(matcherLadyMarie.group(14)), Double.parseDouble(matcherLadyMarie.group(15)));

            // Create and return the DTO
            var modelTrackDTO = ModelTrackDTO.builder()
                    .modelName(Integer.parseInt(matcherLadyMarie.group(3)))
                    .positionX(Float.parseFloat(matcherLadyMarie.group(4)))
                    .positionY(Float.parseFloat(matcherLadyMarie.group(5)));
//                    .speed(Double.parseDouble(matcherLadyMarie.group(6)))
//                    .heading(Double.parseDouble(matcherLadyMarie.group(7)));
//                    .rudder(Double.parseDouble(matcherLadyMarie.group(8)))
//                    .gpsQuality(Double.parseDouble(matcherLadyMarie.group(9))) // gpsQuality
//                    .engine(Double.parseDouble(matcherLadyMarie.group(10))) // engine
//                    .bowThruster(Double.parseDouble(matcherLadyMarie.group(11))); // bowThruster

            // Create and assign TugDTO for bow and stern tugs
//            modelTrackDTO.bowTug(bowTug);

//            modelTrackDTO.sternTug(sternTug); // sternTug;
//            return modelTrackDTO.build();

            return Optional.of(LogItem.builder()
                    .modelTrack(modelTrackDTO.build())
                    .timestamp(getTimestamp(date, time))
                    .build());
            // Parse each value from the match groups and create the DTO object
        } else if (matcherOtherModels.find()) {
            if (isValidModelId(matcherOtherModels)) {
                return Optional.empty();
            }
            var modelTrackDTO = ModelTrackDTO.builder()
                    .modelName(Integer.parseInt(matcherOtherModels.group(3)))
                    .positionX(Float.parseFloat(matcherOtherModels.group(4)))
                    .positionY(Float.parseFloat(matcherOtherModels.group(5)));
//                    .speed(Double.parseDouble(matcherOtherModels.group(6)))
//                    .heading(Double.parseDouble(matcherOtherModels.group(7)));
//                    .rudder(Double.parseDouble(matcherOtherModels.group(8)))
//                    .gpsQuality(Double.parseDouble(matcherOtherModels.group(9))) // gpsQuality
//                    .engine(Double.parseDouble(matcherOtherModels.group(10))) // engine
//                    .bowThruster(Double.parseDouble(matcherOtherModels.group(11))); // bowThruster

            // Create and assign TugDTO for bow and stern tugs
            var bowTug = TugDTO.builder();
            bowTug.tugForce(Double.parseDouble(matcherOtherModels.group(12))); // bowTug.tugForce
            bowTug.tugDirection(Double.parseDouble(matcherOtherModels.group(13))); // bowTug.tugDirection
//            modelTrackDTO.bowTug(bowTug.build());

            var sternTug = TugDTO.builder();
            sternTug.tugForce(Double.parseDouble(matcherOtherModels.group(14))); // sternTug.tugForce
            sternTug.tugDirection(Double.parseDouble(matcherOtherModels.group(15))); // sternTug.tugDirection
//            modelTrackDTO.sternTug(sternTug.build()); // sternTug;
            return Optional.of(LogItem.builder()
                    .timestamp(getTimestamp(matcherOtherModels.group(1), matcherOtherModels.group(2)))
                    .modelTrack(modelTrackDTO.build())
                    .build());
        } else {
            // Handle invalid input
            return Optional.empty();
        }
    }

    private static boolean isValidModelId(Matcher matcherLadyMarie) {
        return Models.fromId(Integer.parseInt(matcherLadyMarie.group(3))) == null;
    }

    private static long getTimestamp(String date, String time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");
        LocalDateTime dateTime = LocalDateTime.parse(date + " " + time, formatter);
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}
