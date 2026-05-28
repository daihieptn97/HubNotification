package com.hieptran.hubnotification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GoogleMapsNavParserTest {
    @Test
    public void parseDecimalKm_sendsMetersToFirmware() {
        GoogleMapsNavParser.NavInfo navInfo = GoogleMapsNavParser.parse(
                "In 1.3 km",
                "Turn right onto Le Loi",
                null
        );

        assertNotNull(navInfo);
        assertEquals("right", navInfo.arrow);
        assertEquals(1300, navInfo.distance);
        assertEquals("m", navInfo.unit);
    }

    @Test
    public void parseDecimalCommaKm_sendsMetersToFirmware() {
        GoogleMapsNavParser.NavInfo navInfo = GoogleMapsNavParser.parse(
                "Sau 0,5 km",
                "Re trai vao Nguyen Trai",
                null
        );

        assertNotNull(navInfo);
        assertEquals("left", navInfo.arrow);
        assertEquals(500, navInfo.distance);
        assertEquals("m", navInfo.unit);
    }
}
