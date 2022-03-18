package com.sn1pe2win.managers.triumphs;

import com.sn1pe2win.managers.triumphs.Specs.DisplayType;

public class TriumphStepCreationSpec {
	
	long requirement = 1;
	boolean display = false;
	boolean forceRole = false;
	int points = 0;
	String displayName = "";
	DisplayType type = DisplayType.VALUE;
	
	public TriumphStepCreationSpec setRequirement(long requirement) {
		this.requirement = requirement;
		return this;
	}
	
	public long getRequirement() {
		return requirement;
	}
	
	public TriumphStepCreationSpec setDisplayType(DisplayType type) {
		this.type = type;
		return this;
	}
	
	public DisplayType getDisplayType() {
		return type;
	}
	
	public TriumphStepCreationSpec setDisplay(boolean display) {
		this.display = display;
		return this;
	}
	
	public boolean isDisplay() {
		return display;
	}
	
	public TriumphStepCreationSpec setPoints(int points) {
		this.points = points;
		return this;
	}
	
	public TriumphStepCreationSpec setForceRole(boolean forceRole) {
		this.forceRole = forceRole;
		return this;
	}
	
	public boolean getForceRole() {
		return forceRole;
	}
	
	public int getPoints() {
		return points;
	}
	
	public TriumphStepCreationSpec setDisplayName(String displayName) {
		this.displayName = displayName;
		return this;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	RS_TriumphStep build(String hookPID) {
		RS_TriumphStep s = new RS_TriumphStep();
		s.display.set(display);
		s.displayName.set(displayName);
		s.points.set(points);
		s.progressId.set(hookPID);
		s.requirement.set(requirement);
		s.displayType.set(type.toString());
		s.forceRole.set(forceRole);
		return s;
	}
	
	public static TriumphStepCreationSpec parse(RS_TriumphStep s) {
		TriumphStepCreationSpec se = new TriumphStepCreationSpec();
		se.display = s.display.get();
		se.displayName = s.displayName.get();
		se.points = s.points.get();
		se.requirement = s.requirement.get();
		se.type = DisplayType.of(s.displayType.get());
		se.forceRole = s.forceRole.get();
		return se;
	}
}
