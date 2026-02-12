package net.atos.mev.calendarcalculator.schedules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.atos.mev.calendarcalculator.Match;

public class ScheduleMoroccoAlg {

	private static final Logger log = LoggerFactory.getLogger(ScheduleMoroccoAlg.class);
	
	SchCalendar calendar;
	SchEnvironment schEnv;
	int lastRoundToAssign;
	int roundsForwardLook;
	int timeAssignsForwardLook;
	String[] competitions;
	HashMap<String, Integer> restdays = new HashMap<String, Integer>();
	int maxMatchesPerDay;
	int maxRestDays = 0;
	int restDaysOfAssignComp = 0;
	// ScheduleMoroccoAlg.maxDaysAdvanceMatch=14
	// ScheduleMoroccoAlg.maxDaysPostponeMatch=14
	int maxDaysAdvanceMatch = 0;
	int maxDaysPostponeMatch = 0;
	int groupsToRun = 1;
	String[] timeslots;
	ArrayList<String[]> teamsNotHomeSameDay = new ArrayList<String[]>();
	ArrayList<String[]> assignGroups = new ArrayList<String[]>();
	ArrayList<Integer> roundsForwardLookPerGroup = new ArrayList<Integer>();
	ArrayList<String> allTeams = new ArrayList<String>();
	int groupToOptimizeTimes;
	String[] teamsToOptimizeTimes;
	String[] groupsCanGoToMAJ;
	String ramadanTimeslot;
	LocalDate ramadanStart;
	LocalDate ramadanEnd;
	int startSecondLegDay;
	String[] teamsWithoutLight;
	String timeslotWithoutLight;
	// List of all Rounds, including rounds of a number, and MAJ's
	ArrayList<SchRound> allRounds = new ArrayList<SchRound>();
	int[] roundCalculationOrder;
	// Map that tells us, for each round of the competition, which round in the calendar is it (because there are MAJ's in the middle)
	HashMap<Integer, Integer> findRoundInRoundList = new HashMap<Integer, Integer>();
	// The following variables are the ones that will be changing during the execution of the recursive algorithm
	MatchToDayAssignmentGroup bestAssignment;
	MatchToDayAssignmentGroup currentAssignment;
	int[] nMatchesPerDay;
	int solutionsFound;
	String outputDatesCSV;
	String outputDatesExcel;
	String outputTeamCSV;
	String outputTeamExcel;
	String outputStatisticsCSV;
	String outputTimeStatisticsCSV;
	boolean failOnNoFeasibleAssignment = false;
	MatchToTimeAssignmentGroup currentTimeAssignment;
	int[] repeatsTimeslotsPer4MatchDay = { 2, 1, 1 };
	static final String EMPTYTIMESLOT = "12:12";
	static final String BYE = "BYE"; // When a team rests, in the calendar it shows as playing against BYE
	
	public ScheduleMoroccoAlg(SchEnvironment schEnv) {
		this.schEnv = schEnv;
		outputDatesCSV = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputDatesCSV");
		outputDatesExcel = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputDatesExcel");
		outputTeamCSV = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputTeamCSV");
		outputTeamExcel = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputTeamExcel");
		outputStatisticsCSV = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputStatisticsCSV");
		outputTimeStatisticsCSV = schEnv.prop.getProperty("ScheduleMoroccoAlg.outputTimeStatisticsCSV");
		failOnNoFeasibleAssignment = Boolean.parseBoolean(
			schEnv.prop.getProperty("ScheduleMoroccoAlg.failOnNoFeasibleAssignment", "false")
		);
		lastRoundToAssign = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.lastRoundToAssign"));
		maxMatchesPerDay = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.maxMatchesPerDay"));
		groupsToRun = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.groupsToRun"));
		// this.roundsForwardLook = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.roundsForwardLook"));
		maxDaysAdvanceMatch = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.maxDaysAdvanceMatch"));
		maxDaysPostponeMatch = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.maxDaysPostponeMatch"));
		timeslots = schEnv.prop.getProperty("ScheduleMoroccoAlg.timeslots").split(",");
		groupToOptimizeTimes = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.groupToOptimizeTimes"));
		teamsToOptimizeTimes = schEnv.prop.getProperty("ScheduleMoroccoAlg.teamsToOptimizeTimes").split(",");
		ramadanTimeslot = schEnv.prop.getProperty("ScheduleMoroccoAlg.ramadan.timeslots");
		ramadanStart = SchEnvironment.readDate(schEnv.prop.getProperty("ScheduleMoroccoAlg.ramadan.start"));
		ramadanEnd = SchEnvironment.readDate(schEnv.prop.getProperty("ScheduleMoroccoAlg.ramadan.end"));
		teamsWithoutLight = schEnv.prop.getProperty("ScheduleMoroccoAlg.teamsWithoutLight").split(",");
		Arrays.sort(teamsWithoutLight);
		timeslotWithoutLight = schEnv.prop.getProperty("ScheduleMoroccoAlg.timeslotWithoutLight");
		competitions = schEnv.prop.getProperty("ScheduleMoroccoAlg.competitions").split(",");
		for (String comp : competitions) {
			int restDaysThisComp = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.restDays." + comp));
			restdays.put(comp, restDaysThisComp);
			if (restDaysThisComp > maxRestDays) {
				maxRestDays = restDaysThisComp;
			}
			if (comp.equals(schEnv.env.code)) {
				restDaysOfAssignComp = restDaysThisComp;
			}
		}
		String propTNHSD = schEnv.prop.getProperty("ScheduleMoroccoAlg.teamsNotHomeSameDay.1");
		for (int i = 2; propTNHSD != null; i++) {
			String[] currentTNHSD = propTNHSD.split(",");
			teamsNotHomeSameDay.add(currentTNHSD);
			propTNHSD = schEnv.prop.getProperty("ScheduleMoroccoAlg.teamsNotHomeSameDay." + i);
		}
		String propAssignGroup = schEnv.prop.getProperty("ScheduleMoroccoAlg.assignGroups.1");
		for (int i = 2; propAssignGroup != null; i++) {
			String[] currentAssignGroup = propAssignGroup.split(",");
			assignGroups.add(currentAssignGroup);
			allTeams.addAll(Arrays.asList(currentAssignGroup));
			int roundsForwardLookThisGroup = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.roundsForwardLook." + (i - 1)));
			roundsForwardLookPerGroup.add(roundsForwardLookThisGroup);
			propAssignGroup = schEnv.prop.getProperty("ScheduleMoroccoAlg.assignGroups." + i);
		}
		timeAssignsForwardLook = Integer.valueOf(schEnv.prop.getProperty("ScheduleMoroccoAlg.timeAssignsForwardLook"));
		groupsCanGoToMAJ = schEnv.prop.getProperty("ScheduleMoroccoAlg.groupsCanGoToMAJ").split(",");
		String[] roundOrderString = schEnv.prop.getProperty("ScheduleMoroccoAlg.roundCalculationOrder").split(",");
		roundCalculationOrder = new int[roundOrderString.length];
		for (int i = 0; i < roundOrderString.length; i++) {
			roundCalculationOrder[i] = Integer.valueOf(roundOrderString[i]);
		}
	}
	
	public SchCalendar execute(SchCalendar input) {
		calendar = input;
		startSecondLegDay = calendar.dateNumber(schEnv.prop.getProperty("ScheduleMoroccoAlg.startSecondLeg"));
		for (int nGroup = 0; nGroup < assignGroups.size() && nGroup < groupsToRun; nGroup++) {
			for (int roundAssigning = 0; roundAssigning <= lastRoundToAssign; roundAssigning++) {
				assignDayOfGroupOfTeams(nGroup, roundAssigning);
			}
		}
		assignTimeOfAllMatches();
		SchCalendarExcelIO.matchdaysToCSV(calendar, schEnv.resultsFolder + "/" + outputDatesCSV);
		SchCalendarExcelIO.matchdaysToExcel(calendar, schEnv.resultsFolder + "/" + outputDatesExcel, schEnv);
		// SchCalendarExcelIO.summaryPerTeamToCSV(this.calendar, schEnv.resultsFolder+"/"+outputTeamCSV);
		SchCalendarExcelIO.summaryPerTeamToExcel(calendar, schEnv.resultsFolder + "/" + outputTeamExcel, schEnv);
		SchCalendarExcelIO.statisticsToCSV(calendar, schEnv.resultsFolder + "/" + outputStatisticsCSV);
		SchCalendarExcelIO.timeStatisticsToCSV(calendar, schEnv.resultsFolder + "/" + outputTimeStatisticsCSV);
		return calendar;
	}
	
	public void assignTimeOfAllMatches() {
		assignRamadanMatches();
		assignMatchesWithoutLight();
		assignTimeOfDaysWithOneMatch();
		prepareListOfAssignments();
		while (currentTimeAssignment.allMatches.size() > 0) {
			log.debug("Times pending to assign: {}", currentTimeAssignment.allMatches.size());
			searchBestTimeAssignment();
			applyBestTimeAssignment(1);
			prepareListOfAssignments();
		}
	}
	
	public void assignRamadanMatches() {
		for (Matchday md : calendar.matchdays) {
			if ((md.date.isEqual(ramadanStart) || md.date.isAfter(ramadanStart)) && (md.date.isEqual(ramadanEnd) || md.date.isBefore(ramadanEnd))) {
				for (SchMatch sm : md.matches) {
					sm.setTime(ramadanTimeslot);
				}
			}
		}
	}
	
	public void assignMatchesWithoutLight() {
		for (Matchday md : calendar.matchdays) {
			for (SchMatch sm : md.matches) {
				if (Arrays.binarySearch(teamsWithoutLight, sm.theMatch.team1.getCode()) >= 0) {
					sm.setTime(timeslotWithoutLight);
				}
			}
		}
	}
	
	public void assignTimeOfDaysWithOneMatch() {
		for (Matchday md : calendar.matchdays) {
			if (md.matches.size() == 1 && md.matches.get(0).getTime().equals(EMPTYTIMESLOT)) {
				md.matches.get(0).setTime(timeslots[0]);
			}
		}
	}
	
	public void prepareListOfAssignments() {
		currentTimeAssignment = new MatchToTimeAssignmentGroup(timeslots.length, teamsToOptimizeTimes);
		for (int nMd = 0; nMd < calendar.matchdays.length; nMd++) {
			Matchday md = calendar.matchdays[nMd];
			if (md.date.isBefore(ramadanStart) || md.date.isAfter(ramadanEnd)) {
				for (int nSm = 0; nSm < md.matches.size(); nSm++) {
					SchMatch sm = md.matches.get(nSm);
					if (sm.getTime().equals(EMPTYTIMESLOT)) {
						currentTimeAssignment.allMatches.add(sm);
						currentTimeAssignment.matchdayOfEachMatch.add(nMd);
						currentTimeAssignment.nInMatchdayOfMatch.add(nSm);
					} else {
						int timeSlot = 0;
						for (int ts = 0; ts < timeslots.length; ts++) {
							if (timeslots[ts].substring(0, 2).equals(sm.getTime().substring(0, 2))) {
								timeSlot = ts;
							}
						}
						currentTimeAssignment.addPreviousMatchTimeAssignment(timeSlot, sm.theMatch.team1.getCode(), sm.theMatch.team2.getCode());
					}
				}
			}
		}
		currentTimeAssignment.allTimeAssignments = new int[currentTimeAssignment.allMatches.size()];
	}
	
	private int countUnassignedMatches(ArrayList<SchMatch> matches) {
		int result = 0;
		for (SchMatch sm : matches) {
			if (sm.time.equals(EMPTYTIMESLOT)) {
				result++;
			}
		}
		return result;
	}
	
	public void searchBestTimeAssignment() {
		if (currentTimeAssignment.assignmentsMade == currentTimeAssignment.allMatches.size() || currentTimeAssignment.assignmentsMade == timeAssignsForwardLook) {
			if (currentTimeAssignment.canBeBetterThanBestAssignment()) {
				// System.out.println("Found new best time assignment");
				currentTimeAssignment.saveAsBestAssignment();
			}
		} else {
			int matchesInTheMatchday = countUnassignedMatches(calendar.matchdays[currentTimeAssignment.matchdayOfEachMatch.get(currentTimeAssignment.assignmentsMade)].matches);
			if (matchesInTheMatchday <= timeslots.length) {
				// 2 or 3 matches in the day
				for (int timeslotTry = 0; timeslotTry < matchesInTheMatchday; timeslotTry++) {
					boolean timeslotIsTaken = false;
					int curIndex = currentTimeAssignment.assignmentsMade;
					boolean sameDayMatch = true;
					for (int previousMatch = 1; sameDayMatch; previousMatch++) {
						int totalIndexPreviousMatch = curIndex - previousMatch;
						if (totalIndexPreviousMatch >= 0) {
							int matchDayPreviousMatch = currentTimeAssignment.matchdayOfEachMatch.get(totalIndexPreviousMatch);
							int matchDayCurrentMatch = currentTimeAssignment.matchdayOfEachMatch.get(curIndex);
							if (matchDayPreviousMatch == matchDayCurrentMatch) {
								if (currentTimeAssignment.allTimeAssignments[currentTimeAssignment.assignmentsMade - previousMatch] == timeslotTry) {
									timeslotIsTaken = true;
								}
							} else {
								sameDayMatch = false;
							}
						} else {
							sameDayMatch = false;
						}
					}
					if (!timeslotIsTaken) {
						currentTimeAssignment.addTimeAssignment(timeslotTry);
						searchBestTimeAssignment();
						currentTimeAssignment.removeLastTimeAssignment();
					}
				}
			} else if (matchesInTheMatchday == timeslots.length + 1) {
				// 4 matches in the day, we can repeat the first time slot
				for (int timeslotTry = 0; timeslotTry < timeslots.length; timeslotTry++) {
					int[] timesSlotUsed = new int[timeslots.length];
					int currentMatchDay = currentTimeAssignment.matchdayOfEachMatch.get(currentTimeAssignment.assignmentsMade);
					for (int previousMatch = 1; currentTimeAssignment.assignmentsMade - previousMatch >= 0 && currentTimeAssignment.matchdayOfEachMatch.get(currentTimeAssignment.assignmentsMade - previousMatch) == currentMatchDay; previousMatch++) {
						timesSlotUsed[currentTimeAssignment.allTimeAssignments[currentTimeAssignment.assignmentsMade - previousMatch]]++;
					}
					if (timesSlotUsed[timeslotTry] < repeatsTimeslotsPer4MatchDay[timeslotTry]) {
						currentTimeAssignment.addTimeAssignment(timeslotTry);
						searchBestTimeAssignment();
						currentTimeAssignment.removeLastTimeAssignment();
					}
				}
				currentTimeAssignment.addTimeAssignment(0);
				if (currentTimeAssignment.canBeBetterThanBestAssignment()) {
					searchBestTimeAssignment();
				}
				currentTimeAssignment.removeLastTimeAssignment();
			} else {
				log.warn("Some matchday has more than 4 matches");
				currentTimeAssignment.addTimeAssignment(0);
				searchBestTimeAssignment();
				currentTimeAssignment.removeLastTimeAssignment();
			}
		}
	}
	
	public void applyBestTimeAssignment(int nCopyMatches) {
		int previousMatchDay = currentTimeAssignment.matchdayOfEachMatch.get(0);
		for (int i = 0; i < currentTimeAssignment.allMatches.size() && (currentTimeAssignment.matchdayOfEachMatch.get(i) == previousMatchDay || i < nCopyMatches); i++) {
			currentTimeAssignment.allMatches.get(i).setTime(timeslots[currentTimeAssignment.bestTimeAssignment[i]]);
			previousMatchDay = currentTimeAssignment.matchdayOfEachMatch.get(i);
		}
	}
	
	public void fillListOfAllRounds() {
		// We prepare a list of the available rounds (and MAJ's) with their dates and their matches
		String lastRoundName = "";
		SchRound currentRound = new SchRound();
		nMatchesPerDay = new int[calendar.getMatchdays().length];
		allRounds = new ArrayList<SchRound>();
		findRoundInRoundList = new HashMap<Integer, Integer>();
		for (int calRound = 0; calRound < calendar.getMatchdays().length; calRound++) {
			if (calendar.getMatchdays()[calRound].getCalendarRound() != null) {
				if (!lastRoundName.equals(calendar.getMatchdays()[calRound].getCalendarRound())) {
					lastRoundName = calendar.getMatchdays()[calRound].getCalendarRound();
					currentRound = new SchRound();
					currentRound.setIdRound(lastRoundName);
					allRounds.add(currentRound);
					if (lastRoundName.matches("[0-9]+")) {
						currentRound.setMAJ(false);
						int roundNumber = Integer.valueOf(lastRoundName) - 1;
						findRoundInRoundList.put(roundNumber, allRounds.size() - 1);
						if (roundNumber < schEnv.tour.rounds.length) {
							for (Match m : schEnv.tour.rounds[roundNumber].matches) {
								SchMatch theSchMatch = calendar.findMatch(m.getSchMatchId());
								if (theSchMatch == null) {
									SchMatch currentMatch = new SchMatch(m);
									currentMatch.setCompetitionRound(lastRoundName);
									currentRound.getTheMatches().add(currentMatch);
								} else {
									currentRound.getTheMatches().add(theSchMatch);
								}
							}
						} else {
							// In the tournament we have only the first leg, we need to take the second round separately
							int firstLegRound = roundNumber - schEnv.tour.rounds.length;
							for (Match m : schEnv.tour.rounds[firstLegRound].matches) {
								Match m2 = new Match(m.getPos2(), m.getPos1(), schEnv.env);
								m2.setTeam1(m.getTeam2());
								m2.setTeam2(m.getTeam1());
								SchMatch theSchMatch = calendar.findMatch(m2.getSchMatchId());
								if (theSchMatch == null) {
									SchMatch currentMatch = new SchMatch(m2);
									currentMatch.setCompetitionRound(lastRoundName);
									currentRound.getTheMatches().add(currentMatch);
								} else {
									currentRound.getTheMatches().add(theSchMatch);
								}
							}
						}
					} else {
						currentRound.setMAJ(true);
					}
				}
				currentRound.getDates().add(calRound);
			}
		}
	}
	
	public void assignDayOfGroupOfTeams(int nGroup, int roundAssigning) {
		log.debug("Assigning group {}", nGroup);
		roundsForwardLook = roundsForwardLookPerGroup.get(nGroup);
		fillListOfAllRounds();
		prepareListOfMatchesToAssign(nGroup, roundAssigning);
		// Add to the list of matches per team the matches that they already had in the calendar
		loadExistingMatchesToAssignment();
		// And then we call the recursive function to assign all matches
		// currentAssignment.differenceTeamsMatchesFriday = currentAssignment.calculateDiffTeamsInWeekday(DayOfWeek.FRIDAY);
		// currentAssignment.differenceTeamsMatchesSaturday = currentAssignment.calculateDiffTeamsInWeekday(DayOfWeek.SATURDAY);
		if (currentAssignment.matchCodes.length > 0) {
			solutionsFound = 0;
			assignNextDayOfMatch(0);
			log.debug("nGroup={}, roundAssigning={}, solutionsFound={}", nGroup, roundAssigning, solutionsFound);
			if (solutionsFound == 0) {
				log.debug("Last unassigned match {} - {}", currentAssignment.nLastMatchNotAssigned, currentAssignment.strLastMatchNotAssigned);
				String message = "No feasible assignment found for competition " + schEnv.env.code
					+ " (group " + nGroup
					+ ", round " + roundAssigning
					+ "). Last unassigned: " + currentAssignment.strLastMatchNotAssigned;
				if (failOnNoFeasibleAssignment) {
					throw new IllegalStateException(message);
				}
				log.warn("{}; continuing with partial calendar", message);
				return;
			}
			// Then we take the matches of the assignment and add them to the Calendar
			copyBestMatchesToCalendar(roundAssigning);
		}
		SchCalendarExcelIO.matchdaysToCSV(calendar, schEnv.resultsFolder + "/outSampleAfterG" + nGroup + "R" + roundAssigning + ".csv");
	}
	
	public void prepareListOfMatchesToAssign(int nGroup, int roundAssigning) {
		// We make a list of which matches we are going to assign, which is the matches not yet assigned for the teams of the current group
		ArrayList<SchMatch> matchesToAssignThisGroup = new ArrayList<SchMatch>();
		Arrays.sort(assignGroups.get(nGroup));
		for (int idxRound = 0; idxRound < roundAssigning + roundsForwardLook && idxRound < lastRoundToAssign; idxRound++) {
			int currentRoudToLoad = roundCalculationOrder[idxRound];
			for (SchRound searchingRound : allRounds) {
				if (!searchingRound.isMAJ) {
					// roundCalculationOrder
					int roundForSearching = Integer.valueOf(searchingRound.getIdRound());
					// if(roundForSearching <= lastRoundToAssign && roundForSearching < roundAssigning+roundsForwardLook) {
					if (roundForSearching == currentRoudToLoad) {
						// We have to check now which matches are assigned, because maybe we did in the previous group
						for (SchMatch sm : searchingRound.getTheMatches()) {
							if (!sm.getTheMatch().getTeam1().getCode().equals(BYE) && !sm.getTheMatch().getTeam2().getCode().equals(BYE)) {
								if (calendar.findMatch(sm.getSchMatchId()) == null) {
									String teamHome = sm.getTheMatch().getTeam1().getCode();
									String teamAway = sm.getTheMatch().getTeam2().getCode();
									if (Arrays.binarySearch(assignGroups.get(nGroup), teamHome) >= 0 || Arrays.binarySearch(assignGroups.get(nGroup), teamAway) >= 0) {
										matchesToAssignThisGroup.add(sm);
									}
								}
							}
						}
					}
				}
			}
		}
		bestAssignment = null;
		boolean canMAJThisGroup = false;
		for (String majGroup : groupsCanGoToMAJ) {
			if (Integer.valueOf(majGroup) - 1 == nGroup) {
				canMAJThisGroup = true;
			}
		}
		currentAssignment = new MatchToDayAssignmentGroup(calendar, matchesToAssignThisGroup.size(), calendar.getMatchdays().length, assignGroups.get(nGroup), allTeams.toArray(new String[allTeams.size()]), canMAJThisGroup, teamsToOptimizeTimes);
		for (int idm = 0; idm < matchesToAssignThisGroup.size(); idm++) {
			String homeTeamCode = matchesToAssignThisGroup.get(idm).getTheMatch().getTeam1().getCode();
			String awayTeamCode = matchesToAssignThisGroup.get(idm).getTheMatch().getTeam2().getCode();
			currentAssignment.matchCodes[idm] = matchesToAssignThisGroup.get(idm).getSchMatchId();
			currentAssignment.theMatchSch[idm] = matchesToAssignThisGroup.get(idm);
			currentAssignment.matchTeamHome[idm] = homeTeamCode;
			currentAssignment.matchTeamAway[idm] = awayTeamCode;
			currentAssignment.roundOfMatch[idm] = Integer.valueOf(matchesToAssignThisGroup.get(idm).getCompetitionRound()) - 1;
		}
	}
	
	public void loadExistingMatchesToAssignment() {
		for (int calRound = 0; calRound < calendar.getMatchdays().length; calRound++) {
			Matchday currentMD = calendar.matchdays[calRound];
			for (SchMatch scm : currentMD.matches) {
				currentAssignment.addPreviousAssignedMatch(calRound, restDaysOfAssignComp, scm.getTheMatch().getTeam1().getCode(), scm.getTheMatch().getTeam2().getCode());
			}
			for (OtherCompetitionDay ocd : currentMD.otherMatches) {
				for (String teamInOtherDay : ocd.getTeams()) {
					currentAssignment.addTeamMatchAssignment(teamInOtherDay, calRound, restdays.get(ocd.competition), false, true);
				}
			}
		}
	}
	
	public void assignNextDayOfMatch(int nMatchAssign) {
		if (nMatchAssign < currentAssignment.matchCodes.length) {
			int firstMatchdayDay = -1;
			int lastMatchdayDay = 0;
			boolean fitsInCalendarRound = false;
			int calendarRoundOfThisRound = findRoundInRoundList.get(currentAssignment.roundOfMatch[nMatchAssign]);
			int roundStartingIn1 = Integer.valueOf(allRounds.get(calendarRoundOfThisRound).getIdRound());
			boolean isSecondLegMatch = roundStartingIn1 > schEnv.tour.rounds.length;
			ArrayList<Integer> datesForThisMatch = allRounds.get(calendarRoundOfThisRound).getDates();
			// We start by the end to start putting matches in Saturdays and Sundays, and discard Friday matches quicker
			for (int nDate = datesForThisMatch.size() - 1; nDate >= 0; nDate--) {
				int currentDayToTry = datesForThisMatch.get(nDate);
				if (currentDayToTry < firstMatchdayDay || firstMatchdayDay == -1) {
					firstMatchdayDay = currentDayToTry;
				}
				if (currentDayToTry > lastMatchdayDay) {
					lastMatchdayDay = currentDayToTry;
				}
				if (tryAssignMatchThisDay(nMatchAssign, currentDayToTry)) {
					fitsInCalendarRound = true;
				}
			}
			if (!fitsInCalendarRound && currentAssignment.canUseMAJ) {
				currentAssignment.amountOfMAJ++;
				if (bestAssignment == null || currentAssignment.amountOfMAJ <= bestAssignment.amountOfMAJ) {
					// Then we have to find a MAJ date for this match
					// System.out.println("Sending " + nMatchAssign + ", " + currentAssignment.matchCodes[nMatchAssign] + " to MAJ");
					for (SchRound majRound : allRounds) {
						// If the match fits in a MAJ round, we check all days of that round, but we don't go further in the calendar to fit it
						// if(majRound.isMAJ() && !weCanFitMatch) {
						if (majRound.isMAJ()) {
							for (Integer currentDayToTry : majRound.getDates()) {
								if (currentDayToTry >= firstMatchdayDay - maxDaysAdvanceMatch && currentDayToTry <= lastMatchdayDay + maxDaysPostponeMatch) {
									if (!isSecondLegMatch && currentDayToTry < startSecondLegDay || isSecondLegMatch && currentDayToTry >= startSecondLegDay) {
										if (nMatchAssign == 0) {
											log.trace("calRound={}, date={}, 2leg={}, 2LegStart={}", roundStartingIn1, currentDayToTry, isSecondLegMatch, startSecondLegDay);
											log.trace("{}-{} trying {}", currentAssignment.matchTeamHome[nMatchAssign], currentAssignment.matchTeamAway[nMatchAssign], currentDayToTry);
										}
										if (tryAssignMatchThisDay(nMatchAssign, currentDayToTry)) {
											// System.out.println("Fits in " + currentDayToTry + " match "+ nMatchAssign + ", " + currentAssignment.matchTeamHome[nMatchAssign] + "-" + currentAssignment.matchTeamAway[nMatchAssign]);
											// } else {
											// System.out.println("NO FIT in " + currentDayToTry + " match "+ nMatchAssign + ", " + currentAssignment.matchTeamHome[nMatchAssign] + "-" + currentAssignment.matchTeamAway[nMatchAssign]);
										}
									}
								}
							}
						}
					}
				}
				currentAssignment.amountOfMAJ--;
			}
		} else {
			if (currentAssignment.canThisAssignementBeBetterThanBest(bestAssignment, false)) {
				// WE HAVE FOUND A CANDIDATE!!
				solutionsFound++;
				// System.out.println("matchesAssignedPerDay="+Arrays.toString(currentAssignment.matchesAssignedPerDay));
				if (solutionsFound % 1000000 == 1) {
					log.debug("================== FOUND SOLUTION {} ==================", solutionsFound);
				}
				bestAssignment = currentAssignment.makeCopy();
				if (currentAssignment.teamsInGroup.length > 3) {
					log.debug(
						"MAJ={}, extremeTeams={}, DaysWith4Matches={}, NotWeekendMatches={}, MatchesOnMonday={}, diffMidweek={}, diffSatSun={}",
						bestAssignment.amountOfMAJ,
						bestAssignment.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate,
						bestAssignment.amountOfDaysWith4Matches,
						bestAssignment.amountOfNotWeekendMatches,
						bestAssignment.amountOfMatchesOnMonday,
						bestAssignment.differenceTeamsMidweek,
						bestAssignment.diffBetweenSatAndSun
					);
				}
			}
		}
	}
	
	public boolean tryAssignMatchThisDay(int nMatchAssign, int currentDayToTry) {
		boolean fitsInCalendarRound = false;
		String homeTeam = currentAssignment.matchTeamHome[nMatchAssign];
		String awayTeam = currentAssignment.matchTeamAway[nMatchAssign];
		if (currentAssignment.nLastMatchNotAssigned < nMatchAssign) {
			currentAssignment.nLastMatchNotAssigned = nMatchAssign;
			currentAssignment.strLastMatchNotAssigned = homeTeam + "-" + awayTeam;
			log.trace("First time assigning {} - {}", nMatchAssign, currentAssignment.strLastMatchNotAssigned);
		}
		// Find if the match is too close to another previous match that we've already assigned for the same team
		int matchEnoughRestHome = currentAssignment.newMatchEnoughRest(homeTeam, currentDayToTry);
		int matchEnoughRestAway = currentAssignment.newMatchEnoughRest(awayTeam, currentDayToTry);
		// if(homeTeam.equals("RSB") && awayTeam.equals("MAS")) {
		// System.out.println("RSB-MAS day " + SchEnvironment.writeDate(calendar.matchdays[currentDayToTry].date) + ", matchEnoughRestAway="+matchEnoughRestAway + ", matchEnoughRestHome="+matchEnoughRestHome + ", matchesDay= " + currentAssignment.matchesAssignedPerDay[currentDayToTry]);
		// }
		if (matchEnoughRestAway >= 0 && matchEnoughRestHome >= 0) {
			fitsInCalendarRound = true;
			if (matchEnoughRestAway >= 1 && matchEnoughRestHome >= 1) {
				if (currentAssignment.matchesAssignedPerDay[currentDayToTry] < maxMatchesPerDay) {
					boolean ifFourthMatchOfDay = false;
					boolean isNonWeekendMatch = false;
					boolean extremeTeamsPlayingSameDay = false;
					boolean extremeTeamsPlayingTwoDaysApart = false;
					boolean isMatchOnMonday = false;
					if (currentAssignment.matchesAssignedPerDay[currentDayToTry] == 3) {
						ifFourthMatchOfDay = true;
					}
					if (!(calendar.matchdays[currentDayToTry].date.getDayOfWeek() == DayOfWeek.SATURDAY) && !(calendar.matchdays[currentDayToTry].date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
						isNonWeekendMatch = true;
					}
					for (String[] extremeTeams : teamsNotHomeSameDay) {
						if (extremeTeams[0].equals(homeTeam) || extremeTeams[1].equals(homeTeam)) {
							String theOtherTeam = extremeTeams[0];
							if (extremeTeams[0].equals(homeTeam)) {
								theOtherTeam = extremeTeams[1];
							}
							if (currentAssignment.assignedMatchesPerTeam.get(theOtherTeam) == null) {
								currentAssignment.assignedMatchesPerTeam.put(theOtherTeam, new ArrayList<>());
							}
							for (SchDayAssignment otherTeamDate : currentAssignment.assignedMatchesPerTeam.get(theOtherTeam)) {
								if (otherTeamDate.isAtHome) {
									int dayDiff = otherTeamDate.calendarDay - currentDayToTry;
									if (dayDiff == 0) {
										// System.out.println(homeTeam+"="+currentDayToTry+","+theOtherTeam+"="+otherTeamDate.calendarDay);
										extremeTeamsPlayingSameDay = true;
									}
									if (dayDiff == 2 || dayDiff == -2) {
										// System.out.println(homeTeam+"="+currentDayToTry+","+theOtherTeam+"="+otherTeamDate.calendarDay);
										extremeTeamsPlayingTwoDaysApart = true;
									}
								}
							}
						}
					}
					if (calendar.matchdays[currentDayToTry].date.getDayOfWeek() == DayOfWeek.MONDAY) {
						// isMatchOnMonday = true;
					}
					if (ifFourthMatchOfDay) {
						currentAssignment.amountOfDaysWith4Matches++;
					}
					if (isNonWeekendMatch) {
						currentAssignment.amountOfNotWeekendMatches++;
					}
					if (extremeTeamsPlayingTwoDaysApart) {
						currentAssignment.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate++;
					}
					if (isMatchOnMonday) {
						currentAssignment.amountOfMatchesOnMonday++;
					}
					int previousDiffFridays = currentAssignment.differenceTeamsMatchesFriday;
					int previousDiffSaturdays = currentAssignment.differenceTeamsMatchesSaturday;
					currentAssignment.addNewAssignedMatch(nMatchAssign, currentDayToTry, restDaysOfAssignComp, homeTeam, awayTeam);
					if (!extremeTeamsPlayingSameDay && currentAssignment.canThisAssignementBeBetterThanBest(bestAssignment, true)) {
						assignNextDayOfMatch(nMatchAssign + 1);
					}
					currentAssignment.removeAssignedMatch(nMatchAssign, currentDayToTry, homeTeam, awayTeam);
					if (ifFourthMatchOfDay) {
						currentAssignment.amountOfDaysWith4Matches--;
					}
					if (isNonWeekendMatch) {
						currentAssignment.amountOfNotWeekendMatches--;
					}
					if (extremeTeamsPlayingTwoDaysApart) {
						currentAssignment.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate--;
					}
					if (isMatchOnMonday) {
						currentAssignment.amountOfMatchesOnMonday--;
					}
					// } else {
					// System.out.println("Backtracking match because there are too many matches on the same day");
				}
			} else {
				log.trace("Backtracking match {} because of future MAJ", nMatchAssign);
			}
		}
		return fitsInCalendarRound;
	}
	
	public void copyBestMatchesToCalendar(int roundAssigning) {
		log.trace("Copying roundAssigning={}", roundAssigning);
		log.trace("Copying roundCalculationOrder[roundAssigning]={}", roundCalculationOrder[roundAssigning]);
		for (int nMatchToCopy = 0; nMatchToCopy < bestAssignment.matchCodes.length; nMatchToCopy++) {
			SchMatch newSchMatch = currentAssignment.theMatchSch[nMatchToCopy];
			if (Integer.valueOf(newSchMatch.getCompetitionRound()) == roundCalculationOrder[roundAssigning]) {
				int newDay = bestAssignment.dayAssigned[nMatchToCopy];
				calendar.getMatchdays()[newDay].getMatches().add(newSchMatch);
				newSchMatch.setDate(calendar.getMatchdays()[newDay].getDate());
				newSchMatch.setCompetition(schEnv.env.code);
				newSchMatch.setTime(EMPTYTIMESLOT);
			}
		}
	}
	
	public static void main(String[] args) {
		Date d = new Date();
		System.out.println("Starting schedule " + d.getTime());
		SchEnvironment schEnv = SchEnvironment.createFromMasterFile();
		d = new Date();
		System.out.println("End schedule " + d.getTime());
		System.out.println("Starting load Excel " + d.getTime());
		SchCalendar result = SchCalendarExcelIO.loadFromExcel(schEnv);
		d = new Date();
		System.out.println("End load Excel " + d.getTime());
		System.out.println("Starting ScheduleMoroccoAlg " + d.getTime());
		ScheduleMoroccoAlg alg = new ScheduleMoroccoAlg(schEnv);
		result = alg.execute(result);
		d = new Date();
		System.out.println("End ScheduleMoroccoAlg " + d.getTime());
		System.out.println("End schedule " + d.getTime());
	}
}
