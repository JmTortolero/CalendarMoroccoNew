package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.ArrayList;

public class OtherCompetitionDay {
	
	String competition;
	String competitionRound;
	String calendarRound;
	LocalDate date;
	ArrayList<String> teams;
	
	public OtherCompetitionDay() {
		teams = new ArrayList<String>();
	}
	
	public String getCompetition() {
		return competition;
	}
	public void setCompetition(String competition) {
		this.competition = competition;
	}
	public String getCompetitionRound() {
		return competitionRound;
	}
	public void setCompetitionRound(String competitionRound) {
		this.competitionRound = competitionRound;
	}
	public String getCalendarRound() {
		return calendarRound;
	}
	public void setCalendarRound(String calendarRound) {
		this.calendarRound = calendarRound;
	}
	public LocalDate getDate() {
		return date;
	}
	public void setDate(LocalDate date) {
		this.date = date;
	}
	public ArrayList<String> getTeams() {
		return teams;
	}
	public void setTeams(ArrayList<String> teams) {
		this.teams = teams;
	}

}
