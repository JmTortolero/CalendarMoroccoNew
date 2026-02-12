package net.atos.mev.calendar.engine;

import java.io.InputStream;
import java.util.Properties;

public interface CalendarEngine {

    byte[] run(Properties properties, InputStream inputStream);
}

