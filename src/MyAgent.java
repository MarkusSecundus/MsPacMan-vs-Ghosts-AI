import controllers.pacman.PacManControllerBase;
import game.core.G;
import game.core.Game;

import java.lang.reflect.Field;
import java.util.*;

public final class MyAgent extends PacManControllerBase
{
	public static final long PILL_SCORE = 10;
	public static final long BIG_PILL_SCORE = 100;

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

		/*
		@Override
		public long getEstimate(Game game) {
			if(isGoal(game)){
				return 0;
			}
			if(game.gameOver()){
				return UNREACHABLE_COST();
			}
			int ghostProximityFactor = 0;
			for(int g = 0;g<4; ++g){
				var ghostLoc = game.getCurGhostLoc(g);
				var currLoc = game.getCurPacManLoc();
				if(game.getEdibleTime(g) > 1) continue;
				for(var neighbor: game.getGhostNeighbours(g)){
					if(neighbor < 0) continue;
					if(game.getCurPacManLoc() == neighbor){
						return UNREACHABLE_COST();
					}
				}
				var ghostDistance = game.getManhattanDistance(game.getCurPacManLoc(), game.getCurGhostLoc(g));
				if(ghostDistance < 5){
					ghostProximityFactor += 5 - ghostDistance;
				}
			}

			long livesFactor = (3L - game.getLivesRemaining()) * 40000;
			//if(ghostProximityFactor > 0){
			//	livesFactor *= 2;
			//}

			return livesFactor + ghostProximityFactor * 5000L  + game.getNumActivePills() * 5L + game.getNumActivePowerPills() * 5L;
		}

		@Override
		public long getActionCost(Game game, Integer movementDirection) {
			var pos = game.getNeighbour(game.getCurPacManLoc(), movementDirection);
			for(int g = 0;g<4; ++g){
				if(game.getCurGhostLoc(g) == pos){
					if(game.getEdibleTime(g) > 1) return 1;
					return UNREACHABLE_COST();
				}
			}
			var pillIndex = game.getPillIndex(pos);
			var powerPillIndex = game.getPowerPillIndex(pos);
			if(pillIndex >= 0 && game.checkPill(pillIndex))
				return 5;
			if(powerPillIndex >= 0 && game.checkPowerPill(powerPillIndex))
				return 5;
			return movementDirection < 0 ? 2000 : 100;
		}
		*/
		@Override
		public long getEstimate(Game game) {
			if(game.gameOver() || game.getLivesRemaining() < initialState.getLivesRemaining()){
				return UNREACHABLE_COST();
			}
			long numPills = game.getNumActivePills() + game.getNumActivePowerPills();

			long ret = 0;
			var pillsInTier = Math.min(4L, numPills);
			ret += pillsInTier * 500L;
			numPills -= pillsInTier;

			pillsInTier = Math.min(10L, numPills);
			ret += pillsInTier * 100L;
			numPills -= pillsInTier;

			pillsInTier = Math.min(40L, numPills);
			ret += pillsInTier * 50L;
			numPills -= pillsInTier;

			ret += numPills * 15L;

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
