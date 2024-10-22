import controllers.pacman.PacManControllerBase;
import game.core.Game;

import java.util.*;

public final class MyAgent extends PacManControllerBase
{

	static void report(String format, Object... args){
		//System.out.println(String.format(format, args));
	}

	List<Integer> currentSolution;

	@Override
	public void tick(Game game, long timeDue) {
		if(false && currentSolution != null && currentSolution.size() > 50){
			pacman.set(currentSolution.get(0));
			currentSolution.remove(0);
			return;
		}

		var currentTime = System.currentTimeMillis();
		var timeForComputation = timeDue - currentTime;

		var problem = new PacmanProblem(game);
		var solution = doAStar(problem, currentTime + (long)(timeForComputation * 0.9f));
		currentSolution = solution;
		report("time: %d", game.getLevelTime());

		if(solution == null || solution.isEmpty()){
			report("RANDOM!");
			boolean includeReverse = game.rand().nextFloat() < 0.05f;
			int[] directions = game.getPossiblePacManDirs(includeReverse);
			pacman.set(directions[game.rand().nextInt(directions.length)]);
		}
		else{
			pacman.set(solution.get(0));
		}
	}

	@Override
	public void nextLevel(Game game) {
		super.nextLevel(game);

		var timePerAStarIteration = timeSpentInAStarSoFar_nanos / aStarIterationsPerformedSoFar;
		var timePerAStarRun = timeSpentInAStarSoFar_nanos / aStarRunsPerformedSoFar;
		System.err.println(String.format("Level %d... %d ns per aStar iteration, %d ns per run (%d iterations, %d runs, %d ns total)",game.getCurLevel()-1, timePerAStarIteration, timePerAStarRun, aStarIterationsPerformedSoFar,aStarRunsPerformedSoFar, timeSpentInAStarSoFar_nanos));
		timeSpentInAStarSoFar_nanos = 0;
		aStarIterationsPerformedSoFar = 0;
	}

	public static record PacmanProblem(Game initialState) implements HeuristicProblem<Game, Integer>{

		@Override
		public Game getInitialState() {
			return initialState;
		}

		@Override
		public long getEstimate(Game game) {
			if(game.gameOver()){
				if(game.getLivesRemaining() > 0)
					return 0;
				else return UNREACHABLE_COST();
			}
			if(game.getLivesRemaining() < initialState.getLivesRemaining()){
				return UNREACHABLE_COST();
			}
			long initialPills = initialState.getNumActivePills() + initialState.getNumActivePowerPills();
			long numPills = game.getNumActivePills() + game.getNumActivePowerPills();
			if(initialPills <= 15){
				if(numPills < initialPills && numPills <= 6){
					return 0;
				}
				return game.getDistanceToNearestPill() * 50L;
			}

			if(numPills < initialPills && numPills <= 6){
				return 0;
			}

			return numPills * 100L;
		}

		@Override
		public long getActionCost(Game game, Integer movementDirection) {
			if(movementDirection < 0) return 5;
			return 1;
		}

		@Override
		public long UNREACHABLE_COST() {
			return 9999999;
		}

		@Override
		public List<Integer> getAvailableActions(Game game) {
			if(game.gameOver())
				return List.of();

			var ret = new ArrayList<Integer>(4);
			for(var dir : game.getPossiblePacManDirs(false))
				ret.add(dir);
			//ret.add(- ( 1 + game.getReverse(game.getCurPacManDir())) ); //direction backwards as negative number so that we can distinguish it from the others
			return ret;
		}


		@Override
		public Game getActionResult(Game game, Integer direction, boolean isTailOperation) {
			int dir = direction;
			Game ret = game.copy();// isTailOperation ? game : game.copy();
			if(dir < 0) dir = (-dir) - 1;
			ret.advanceGame(dir);
			if(ret.getLevelTime() > 2700){
				try{
					// reset levelTime so that the AI doesn't go totally insane when the end is near
					var field = ret.getClass().getDeclaredField("levelTime");
					field.setAccessible(true);
					field.set(ret, 100);
				}catch(Exception e){throw new RuntimeException(e);}
			}
			return ret;
		}
	}



	public static interface HeuristicProblem<TState, TAction>{
		TState getInitialState();
		long getEstimate(TState state);
		long getActionCost(TState state, TAction action);
		long UNREACHABLE_COST();


		List<TAction> getAvailableActions(TState state);
		TState getActionResult(TState state, TAction action, boolean isTailOperation);
	}




	static record AStarNode<TState, TAction>(
			AStarNode<TState, TAction> last,
			TAction action,
			TState state,
			long cost,
			long estimatedCost
	) implements Comparable<AStarNode<TState, TAction>>

	{
		@Override
		public int compareTo(AStarNode<TState, TAction> o) {
			return Long.compare(estimatedCost, o.estimatedCost);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof AStarNode<?,?> o && cost == o.cost && estimatedCost == o.estimatedCost;
		}

		@Override
		public int hashCode() {
			return Objects.hash(action, state, cost, estimatedCost);
		}

		public List<TAction> getSolution(){
			var ret = new ArrayList<TAction>();
			for(var n = this; n != null ; n = n.last)
				if(n.action != null)
					ret.add(n.action);
			return ret.reversed();
		}

		public static<TState, TAction> AStarNode<TState, TAction> makeEmpty(TState initialState, long estimate){
			return new AStarNode<TState, TAction>(null, null, initialState, 0, estimate);
		}
		public AStarNode<TState, TAction> makeNext(TAction action, TState state, long addedCost, long estimate){
			return new AStarNode<TState, TAction>(this, action, state, cost + addedCost, cost + addedCost + estimate);
		}
	}

	public static long aStarIterationsPerformedSoFar = 0;
	public static long aStarRunsPerformedSoFar = 0;
	public static long timeSpentInAStarSoFar_nanos = 0;

	public static<TState, TAction> List<TAction> doAStar(HeuristicProblem<TState, TAction> problem, long timeDue){
		++aStarRunsPerformedSoFar;
		var initialState = problem.getInitialState();
		var initialNode = AStarNode.<TState, TAction>makeEmpty(initialState, problem.getEstimate(initialState));

		var queue = new PriorityQueue<AStarNode<TState, TAction>>();
		queue.add(initialNode);

		long startTime = System.nanoTime();
		while(System.currentTimeMillis() < timeDue && !queue.isEmpty()){
			++aStarIterationsPerformedSoFar;
			var current = queue.poll();

			var availableActions = problem.getAvailableActions(current.state);
			for(int t=0;t<availableActions.size();++t){
				var action = availableActions.get(t);
				boolean isLast = current != initialNode && (t == (availableActions.size() - 1));
				var cost = problem.getActionCost(current.state, action);
				var resultState = problem.getActionResult(current.state, action, isLast);
				//if(isLast) current.state = null;
				var estimate = problem.getEstimate(resultState);

				if(cost + estimate >= problem.UNREACHABLE_COST())
					continue;
				var newNode = current.makeNext(action, resultState, cost, estimate);
				if(estimate == 0){
					//goal state
					report("GOAL SOLUTION!");
					timeSpentInAStarSoFar_nanos += System.nanoTime() - startTime;
					return newNode.getSolution();
				}
				queue.add(newNode);
			}
		}
		timeSpentInAStarSoFar_nanos += System.nanoTime() - startTime;

		if(queue.isEmpty())
			return null;

		var top = queue.peek();
		var solution = top.getSolution();
		report("solution... length: %d, cost: %d, estimate: %d", solution.size(), top.cost, top.estimatedCost);
		return solution;
	}

}
