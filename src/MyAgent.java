import controllers.pacman.PacManControllerBase;
import game.core.G;
import game.core.Game;

import java.lang.reflect.Field;
import java.util.*;

public final class MyAgent extends PacManControllerBase
{

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
		System.out.println(String.format("time: %d", game.getLevelTime()));

		if(solution == null || solution.isEmpty()){
			System.out.println("RANDOM!");
			boolean includeReverse = game.rand().nextFloat() < 0.05f;
			int[] directions = game.getPossiblePacManDirs(includeReverse);
			pacman.set(directions[game.rand().nextInt(directions.length)]);
		}
		else{
			pacman.set(solution.get(0));
		}
	}


	public static record PacmanProblem(Game initialState) implements HeuristicProblem<Game, Integer>{

		@Override
		public Game getInitialState() {
			return initialState;
		}

		@Override
		public long getEstimate(Game game) {
			if(game.gameOver() || game.getLivesRemaining() < initialState.getLivesRemaining()){
				return UNREACHABLE_COST();
			}
			long initialPills = initialState.getNumActivePills() + initialState.getNumActivePowerPills();
			long numPills = game.getNumActivePills() + game.getNumActivePowerPills();
			if(initialPills <= 10){
				if(numPills < initialPills && numPills <= 6){
					return 0;
				}
				return game.getDistanceToNearestPill() * 50L;
			}

			if(numPills < initialPills && numPills <= 6){
				return 0;
			}

			long ret = 0;
			long pillsInTier;
			//pillsInTier = Math.min(4L, numPills);
			//ret += pillsInTier * 5L;
			//numPills -= pillsInTier;

			//pillsInTier = Math.min(10L, numPills);
			//ret += pillsInTier * 10L;
			//numPills -= pillsInTier;

			//pillsInTier = Math.min(40L, numPills);
			//ret += pillsInTier * 50L;
			//numPills -= pillsInTier;

			ret += numPills * 100L;
			return ret;
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
		public Iterable<Integer> getAvailableActions(Game game) {
			if(game.gameOver())
				return List.of();

			var ret = new ArrayList<Integer>(4);
			for(var dir : game.getPossiblePacManDirs(false))
				ret.add(dir);
			//ret.add(- ( 1 + game.getReverse(game.getCurPacManDir())) ); //direction backwards as negative number
			return ret;
		}


		@Override
		public Game getActionResult(Game game, Integer direction) {
			int dir = direction;
			var ret = game.copy();
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


		Iterable<TAction> getAvailableActions(TState state);
		TState getActionResult(TState state, TAction action);
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


	public static<TState, TAction> List<TAction> doAStar(HeuristicProblem<TState, TAction> problem, long timeDue){
		var initialState = problem.getInitialState();
		var initialNode = AStarNode.<TState, TAction>makeEmpty(initialState, problem.getEstimate(initialState));

		var queue = new PriorityQueue<AStarNode<TState, TAction>>();
		queue.add(initialNode);

		while(System.currentTimeMillis() < timeDue && !queue.isEmpty()){
			var current = queue.poll();

			for(var action : problem.getAvailableActions(current.state)){
				var cost = problem.getActionCost(current.state, action);
				var resultState = problem.getActionResult(current.state, action);
				var estimate = problem.getEstimate(resultState);

				if(cost + estimate >= problem.UNREACHABLE_COST())
					continue;
				var newNode = current.makeNext(action, resultState, cost, estimate);
				if(estimate == 0){
					//goal state
					System.out.println("GOAL SOLUTION!");
					return newNode.getSolution();
				}
				queue.add(newNode);
			}
		}

		if(queue.isEmpty())
			return null;

		var top = queue.peek();
		var solution = top.getSolution();
		System.out.println(String.format("solution... length: %d, cost: %d, estimate: %d", solution.size(), top.cost, top.estimatedCost));
		return solution;
	}

}
