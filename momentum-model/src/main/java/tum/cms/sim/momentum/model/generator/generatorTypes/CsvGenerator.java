package tum.cms.sim.momentum.model.generator.generatorTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import tum.cms.sim.momentum.configuration.model.output.WriterSourceConfiguration.OutputType;
import tum.cms.sim.momentum.data.agent.pedestrian.state.other.StaticState;
import tum.cms.sim.momentum.infrastructure.execute.SimulationState;
import tum.cms.sim.momentum.model.generator.Generator;
import tum.cms.sim.momentum.utility.geometry.GeometryFactory;

public class CsvGenerator extends Generator {

	private static String csvInputName = "csvInput";
	private static String csvMappingName = "csvMapping";
	private static String timeStepMappingName = "timeStepMapping";
	private static String containsHeaderName = "containsHeader";
	
	private double timeStepMapping = 0.0;
	private int timeStepIndex = -1;
	private int idIndex = -1;
	private int xIndex = -1;
	private int yIndex = -1;
 
	private HashMap<Integer, ArrayList<String>> csvMapping;
	private HashMap<Long, HashMap<Integer, CsvGeneratorDataObject>> generationSet = new HashMap<>();
	// the next set is used to compute the heading of a pedestrian and the velocity
	private HashMap<Integer, CsvGeneratorDataObject> generationNextSet = new HashMap<>();
	
	@Override
	public void callPreProcessing(SimulationState simulationState) {
		
		this.timeStepMapping = this.properties.getDoubleProperty(timeStepMappingName);
		List<String> csvInput = this.properties.<String>getListProperty(csvInputName);
		
		for(int iter = 0; iter < csvInput.size(); iter++) {
			
			if(OutputType.valueOf(csvInput.get(iter)) == OutputType.timeStep) {
				
				timeStepIndex = iter;
			}
			
			if(OutputType.valueOf(csvInput.get(iter)) == OutputType.id) {
				
				idIndex = iter;
			}
			
			if(OutputType.valueOf(csvInput.get(iter)) == OutputType.x) {
						
				xIndex = iter;
			}
			
			if(OutputType.valueOf(csvInput.get(iter)) == OutputType.y) {
				
				yIndex = iter;
			}
		}
		
		this.csvMapping = this.properties.<String>getMatrixProperty(csvMappingName);
		
		if(this.properties.getBooleanProperty(containsHeaderName)) {
			this.csvMapping.remove(0);
		}
		
		// compute generation map

		for(ArrayList<String> data : csvMapping.values()) {
			
			int id = Integer.parseInt(data.get(idIndex));
			long dataTimeStep = Long.parseLong(data.get(timeStepIndex));
			
			// Translate data time step to simulation time step
			// E.g. 
			// timeStepMapping is 0.04 -> means 1 dataTimeStep is 0.04 seconds
			// getTimeStepDuration is 0.1 ->  means 1 simulationTimeStep is 0.1 seconds
			// Caluclation with 132 dataTime Step 
			// (int)(131 * 0.04 seconds / 0.1 seconds) + 0.5 = (int)(5.24 seconds / 0.1 seconds) + 0.5 
			// = (int)(52.4 + 0.5) = (int)(52.9) = 52 simulation time step
			// that is 52 * 0.1 = 5.2 seconds in simulation time
			long simulationTimeStep = (long)((this.timeStepMapping * dataTimeStep) / simulationState.getTimeStepDuration() + 0.5);
			
			this.generationSet.putIfAbsent(simulationTimeStep, new HashMap<>());
			
			// add pedestrian to be generated in the generation set
			if(!this.generationSet.get(dataTimeStep).containsKey(id)) {
				
				this.generationSet.get(dataTimeStep).put(id, new CsvGeneratorDataObject(data));
			} 
			else if(!this.generationNextSet.containsKey(id)) {
				
				// if the pedestrian is part of the generation set but not of the next generation set add the next data 
				this.generationNextSet.put(id, new CsvGeneratorDataObject(data));
			}
		}
	}

	@Override
	public void execute(Collection<? extends Void> splittTask, SimulationState simulationState) {
		
		if(this.generationSet.containsKey(simulationState.getCurrentTimeStep())) {
			
			for(Entry<Integer, CsvGeneratorDataObject> data : this.generationSet.get(simulationState.getCurrentTimeStep()).entrySet()) {
				
				int id = data.getKey();
				double x = data.getValue().getDouble(xIndex);
				double y = data.getValue().getDouble(yIndex);
				
				double velocityX = 0.0;
				double velocityY = 0.0;
				double headingX = 0.0;
				double headingY = 1.0;
				
				// get next state if exists 
				if(this.generationNextSet.containsKey(id)) {
					
					double xNext = this.generationNextSet.get(id).getDouble(xIndex);
					double yNext = this.generationNextSet.get(id).getDouble(yIndex);	
					double timeDifference = (this.generationNextSet.get(id).getInteger(timeStepIndex) - data.getValue().getInteger(timeStepIndex))
							* simulationState.getTimeStepDuration();
					
					// compute velocity
					velocityX = (xNext - x) * timeDifference;
					velocityY = (yNext - y) * timeDifference;
					
					// compute heading
					velocityX = (xNext - x);
					velocityY = (yNext - y);
				}
			
				StaticState staticState = pedestrianSeed.generateStaticState(-1, this.scenarioManager.getScenarios().getId());
				
				pedestrianManager.createPedestrian(staticState, 
						null,
						GeometryFactory.createVector(x, y), 
						GeometryFactory.createVector(headingX, headingY), 
						GeometryFactory.createVector(velocityX, velocityY).getNormalized(), 
						simulationState.getCurrentTime());
			}
			
			this.generationSet.remove(simulationState.getCurrentTimeStep());
		}
	}

	@Override
	public void callPostProcessing(SimulationState simulationState) {
		// Nothing to do
	}
	
	private class CsvGeneratorDataObject {
		
		private ArrayList<String> dataObject = null;
		
		CsvGeneratorDataObject(ArrayList<String> dataObject) {
			
			this.dataObject = dataObject;
		}
		
		public int getInteger(int index) {
			
			return Integer.parseInt(dataObject.get(index));
		}
		
		public double getDouble(int index) {
			
			return Double.parseDouble(dataObject.get(index));
		}
	}
}
