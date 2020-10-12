package game.controllers.pacman.examples;

import game.controllers.pacman.PacManHijackController;
import game.core.G;
import game.core.Game;

public final class RandomPacMan extends PacManHijackController
{
	@Override
	public void tick(Game game, long timeDue) {
		int[] directions=game.getPossiblePacManDirs(true);		//set flag as true to include reversals		
		pacman.set(directions[G.rnd.nextInt(directions.length)]);
	}
}
