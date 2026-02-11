package net.atos.mev.calendar.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Component;

import net.atos.mev.calendarcalculator.schedules.SchCalendar;
import net.atos.mev.calendarcalculator.schedules.SchCalendarExcelIO;
import net.atos.mev.calendarcalculator.schedules.SchEnvironment;
import net.atos.mev.calendarcalculator.schedules.ScheduleMoroccoAlg;

@Component
public class ScheduleFacade {

    public byte[] run(Properties properties, InputStream inputStream) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            SchEnvironment schEnvironment = SchEnvironment.createFromProperties(properties);
            SchCalendar calendar = SchCalendarExcelIO.loadFromExcel(inputStream, schEnvironment);
            ScheduleMoroccoAlg algorithm = new ScheduleMoroccoAlg(schEnvironment);
            SchCalendar result = algorithm.execute(calendar);
            SchCalendarExcelIO.matchdaysToExcel(result, outputStream, schEnvironment);

            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Error running calendar generation", exception);
        }
    }
}
