package com.sn1pe2win.managers.triumphs;

import java.util.ArrayList;
import java.util.List;

import com.sn1pe2win.managers.triumphs.Specs.DisplayType;
import com.sn1pe2win.managers.triumphs.Specs.TriumphCheckpointCreationSpec;
import com.sn1pe2win.sql.simpledb.RS_Function;

import discord4j.rest.util.Color;

/**Es gibt vier Typen von Triumphen:<br><ul>
 * <li>Einfach: Ein Triumph mit einem Schritt. Ein Triumph ist einfach wenn {@link TriumphCreationSpec#steps} einen Eintrag hat</li>
 * <li>Mehrere Schritte: Gleich wie oben nur mehrere Steps</li>
 * <li>Checklist: {@link TriumphCreationSpec#steps} besitzt einen Eintrag und {@link TriumphCreationSpec#objectives} ist nicht leer. Hierbei dürfen die Untertriumphe aber nur einen Schritt haben und nicht in der triumph table definiert sein.</li>
 * <li>Siegel: Speziell. Dann ist die {@link TriumphCreationSpec#category} "seal"</li>
 * 
 * Für entwicklungszwecken kann diese Klasse auch von einer Datenbank geparsed werden.*/
public class TriumphCreationSpec {	
	
	public enum TriumphType {
		SIMPLE, STEPS, CHECKLIST, UNKNOWN;
	}
	
	ArrayList<TriumphCheckpointCreationSpec> objectives = new ArrayList<>();
	ArrayList<TriumphStepCreationSpec> steps = new ArrayList<>();
	
	final String progressId;
	String origin = "";
	Color color = Color.of(0, 0, 0);
	boolean display = false;
	String category = "general";
	String description = "";
	String iconURL = "";
	String name = "";
	boolean deactivated = false;
	
	TriumphCreationSpec(String progressId) {
		this.progressId = progressId;
	}

	public TriumphCreationSpec setDeactivated(boolean deactivated) {
		this.deactivated = deactivated;
		return this;
	}
	
	public boolean isDeactivated() {
		return deactivated;
	}
	
	public TriumphCreationSpec setColor(Color color) {
		this.color = color;
		return this;
	}
	
	public Color getColor() {
		return color;
	}
	
	public TriumphCreationSpec setName(String name) {
		this.name = name;
		return this;
	}
	
	public String getName() {
		return name;
	}
	
	public TriumphCreationSpec setDisplay(boolean display) {
		this.display = display;
		return this;
	}
	
	public boolean isDisplay() {
		return display;
	}
	
	public TriumphCreationSpec setCategory(String category) {
		this.category = category;
		return this;
	}
	
	public String getCategory() {
		return category;
	}
	
	public TriumphCreationSpec setDescription(String description) {
		this.description = description;
		return this;
	}
	
	public String getDescription() {
		return description;
	}
	
	public TriumphCreationSpec setIcon(String iconURL) {
		this.iconURL = iconURL;
		return this;
	}
	
	public String getIcon() {
		return iconURL;
	}
	
	public TriumphStepCreationSpec getStepForProgressValue() {
		//TODO
		return null;
	}
	
	/**May return an empty array if the type is not {@link TriumphType#CHECKLIST} Check with {@link TriumphCreationSpec#getTriumphtype()}*/
	public TriumphCheckpointCreationSpec[] getCheckpoints() {
		if(getTriumphtype() == TriumphType.CHECKLIST) {
			return objectives.toArray(new TriumphCheckpointCreationSpec[objectives.size()]);
		} else return new TriumphCheckpointCreationSpec[] {};
	}
	
	/**Never empty.*/
	public TriumphStepCreationSpec[] getSteps() {
		if(getTriumphtype() == TriumphType.CHECKLIST) {
			return steps.toArray(new TriumphStepCreationSpec[steps.size()]);
		} else return new TriumphStepCreationSpec[] {};
	}
	
	public TriumphType getTriumphtype() {
		if(steps.size() == 1 && objectives.size() == 0) return TriumphType.SIMPLE;
		else if(steps.size() > 1 && objectives.size() == 0) return TriumphType.STEPS;
		else if(steps.size() == 1 && objectives.size() > 0) return TriumphType.CHECKLIST;
		else return TriumphType.UNKNOWN;
	}
	
	/**Für die Triumph Tabelle*/
	RS_Triumph buildTriumphData() {
		RS_Triumph t = new RS_Triumph();
		t.category.set(category);
		t.description.set(description);
		t.iconURL.set(iconURL);
		t.progressId.set(progressId);
		t.color.set(color.getRGB());
		t.origin.set(origin);
		t.name.set(name);
		t.deactivated.set(deactivated);
		return t;
	}
	
	RS_TriumphStep[] buildStepData() {
		RS_TriumphStep[] steps = new RS_TriumphStep[this.steps.size()];
		for(int i = 0; i < steps.length; i++) 
			steps[i] = this.steps.get(i).build(this.progressId);
		return steps;
	}
	
	RS_TriumphStep[] buildChecklistData() {
		RS_TriumphStep[] steps = new RS_TriumphStep[this.objectives.size()];
		for(int i = 0; i < steps.length; i++) 
			steps[i] = this.objectives.get(i).build(this.objectives.get(i).progressID);
		return steps;
	}
	
	/**Gibt alle dazugehörigen Triumphdaten zu einer beliebigen ProgressID zurück.
	 * Es ist auch möglich die ProgressID eines checklisten Schrittes anzugeben.
	 * In allen Fällen wird der vollständige Triumph zurückgeliefert.*/
	public static TriumphCreationSpec parse(String progressID, TriumphManager manager) {
		RS_Function<RS_Triumph> func = manager.getHandler().getDatabase().createGetFunction(RS_Triumph.class, "SELECT * FROM " + TriumphManager.TABLE_TRIUMPHS + " WHERE progressID = \"" + progressID + "\"");
		List<RS_Triumph> t = func.get(false);
		
		if(t.size() == 0) {
			//Vielleicht eine checklist progress id? CHECKEN!
			RS_Function<RS_TriumphObjective> checklistPID = manager.getHandler().getDatabase().createGetFunction(RS_TriumphObjective.class, "SELECT parentID FROM " + TriumphManager.TABLE_TRIUMPH_OBJECTIVES + " WHERE objectiveID = \"" + progressID + "\"");
			List<RS_TriumphObjective> parent = checklistPID.get(false);
			if(parent.size() == 0) return null;
			
			progressID = parent.get(0).parentId.getAsString();
			func = manager.getHandler().getDatabase().createGetFunction(RS_Triumph.class, "SELECT * FROM " + TriumphManager.TABLE_TRIUMPHS + " WHERE progressID = \"" + progressID + "\"");
			t = func.get(false);
		}
		
		RS_Triumph triumphData = t.get(0);
		TriumphCreationSpec spec = new TriumphCreationSpec(progressID);
		spec.category = triumphData.category.get();
		spec.description = triumphData.description.get();
		spec.color = Color.of(triumphData.color.get());
		spec.iconURL = triumphData.iconURL.get();
		spec.origin = triumphData.origin.get();
		spec.name = triumphData.name.get();
		spec.deactivated = triumphData.deactivated.get();
		
		//Checke of es von diesem Triumph mehrere Schritte gibt
		RS_Function<RS_TriumphStep> fsteps = manager.getHandler().getDatabase().createGetFunction(RS_TriumphStep.class, "SELECT * FROM " + TriumphManager.TABLE_TRIUMPH_STEP + " WHERE progressID = \"" + progressID + "\"");
		List<RS_TriumphStep> steps = fsteps.get(false);
		
		if(steps.size() > 0) {
			//Füge die Schritte hinzu
			for(RS_TriumphStep s : fsteps.getCached()) {
				TriumphStepCreationSpec step = new TriumphStepCreationSpec();
				step.display = s.display.get();
				step.displayName = s.displayName.get();
				step.points = s.points.get();
				step.requirement = s.requirement.get();
				step.type = DisplayType.of(s.displayType.get());
				spec.steps.add(step);
			}
		} 
		
		if(steps.size() == 1) {
			RS_Function<RS_TriumphObjective> fobj = manager.getHandler().getDatabase().createGetFunction(RS_TriumphObjective.class, "SELECT * FROM " + TriumphManager.TABLE_TRIUMPH_OBJECTIVES + " WHERE parentID = \"" + progressID + "\"");
			List<RS_TriumphObjective> obj = fobj.get(false);
			
			if(obj.size() > 0) {
				String query = "SELECT * FROM " + TriumphManager.TABLE_TRIUMPH_STEP + " WHERE ";
				for(int i = 0; i < obj.size(); i++) {
					query += "progressID = \"" + obj.get(i).objectiveId.get() + "\"" + (i == obj.size()-1 ? "" : " OR ");
				}
				RS_Function<RS_TriumphStep> objectives = manager.getHandler().getDatabase().createGetFunction(RS_TriumphStep.class, query);
				for(RS_TriumphStep s : objectives.get(false)) {
					TriumphCheckpointCreationSpec cp = new TriumphCheckpointCreationSpec(s.progressId.get());
					cp.display = s.display.get();
					cp.displayName = s.displayName.get();
					cp.points = s.points.get();
					cp.requirement = s.requirement.get();
					cp.type = DisplayType.of(s.displayType.get());
					spec.objectives.add(cp);
				}
			}
		}
		
		return spec;
	}
}
