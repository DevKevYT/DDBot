package com.sn1pe2win.managers.triumphs;

public interface Specs {
	
	public enum DisplayType {
		
		/**Just display the current value*/
		VALUE, 
		/**Display the progress in percent*/
		PERCENTAGE,
		/**Only display this triumph if it got completed. Useful for quests, eastereggs or whatever you want to do with it :)*/
		HIDDEN;
		
		/**If the type is unknown, it defaults on progress. Not case sensitive*/
		public static DisplayType of(String value) {
			for(DisplayType type : values()) {
				if(value.toLowerCase().equals(type.toString().toLowerCase())) return type;
			}
			return VALUE;
		}
	}
	
	public class ChecklistTriumphCreationSpec extends SimpleTriumphCreationSpec {
		
		public ChecklistTriumphCreationSpec(String progressId) {
			super(progressId);
		}

		public ChecklistTriumphCreationSpec addChecklistEntry(TriumphCheckpointCreationSpec spec) {
			this.objectives.add(spec);
			return this;
		}
	}
	
	public class TriumphCheckpointCreationSpec extends TriumphStepCreationSpec {
		
		final String progressID;
		
		public TriumphCheckpointCreationSpec(String progressID) {
			this.progressID = progressID;
		}
		
		@Override
		public TriumphCheckpointCreationSpec setRequirement(long requirement) {
			this.requirement = requirement;
			return this;
		}
		
		@Override
		public TriumphCheckpointCreationSpec setDisplay(boolean display) {
			this.display = display;
			return this;
		}
		
		@Override
		public TriumphCheckpointCreationSpec setPoints(int points) {
			this.points = points;
			return this;
		}
		
		@Override
		public TriumphCheckpointCreationSpec setDisplayName(String displayName) {
			this.displayName = displayName;
			return this;
		}
		
		@Override
		public TriumphCheckpointCreationSpec setForceRole(boolean forceRole) {
			this.forceRole = forceRole;
			return this;
		}
	}
	
	/**Erstellt ein step object welches alles beschreibt*/
	public class SimpleTriumphCreationSpec extends TriumphCreationSpec {

		private final TriumphStepCreationSpec step;
		
		public SimpleTriumphCreationSpec(String progressId) {
			super(progressId);
			step = new TriumphStepCreationSpec();
			this.steps.add(step);
		}
		
		public SimpleTriumphCreationSpec setRequirement(long requirement) {
			this.step.requirement = requirement;
			return this;
		}
		
		public SimpleTriumphCreationSpec setDisplay(boolean display) {
			this.step.display = display;
			return this;
		}
		
		public SimpleTriumphCreationSpec setPoints(int points) {
			this.step.points = points;
			return this;
		}
		
		public SimpleTriumphCreationSpec setDisplayName(String displayName) {
			this.step.displayName = displayName;
			return this;
		}
		
		public SimpleTriumphCreationSpec setForceRole(boolean forceRole) {
			this.step.forceRole = forceRole;
			return this;
		}
	}
	
	public class StepTriumphCreationSpec extends TriumphCreationSpec {
		
		public StepTriumphCreationSpec(String progressId) {
			super(progressId);
		}

		public StepTriumphCreationSpec addStep(TriumphStepCreationSpec step) {
			this.steps.add(step);
			return this;
		}
	}

}
