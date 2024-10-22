import controllers.ghosts.game.GameGhosts;
import game.SimulatorConfig;
import game.core.Game;
import game.core._G_;

// Written by Jakub Hroník


public class JakubHronik_Microbench {

    static final int WARMUP_ITERATIONS = 500000;
    static final int BENCHMARK_ITERATIONS = 500000;
    static final int SEED = 4561623;

    @FunctionalInterface
    public static interface Benchmark{
        public Object run();
    }


    public static void main(String[] args) {

        for(int t=1;t<=50;++t){
            System.out.printf("%d...%n", t);
            testForwardModelClone();
            testForwardModelAdvance();
        }
    }


    static Game _prepareGame(){
        SimulatorConfig config = new SimulatorConfig();
        config.ghostsController = new GameGhosts(4);
        config.game.startingLevel = 1;
        config.pacManController = new JakubHronik();
        config.game.seed = SEED;
        _G_ game = new _G_();
        game.newGame(config.game, config.ghostsController);

        config.ghostsController.reset(game);
        config.pacManController.reset(game);

        game.advanceGame(0);
        return game;
    }

    static void testForwardModelClone(){
        Game game = _prepareGame();

        long benchmarkedTime = measureExecutionTime(game::copy);

        System.out.printf("forward model clone: %d ns\n", benchmarkedTime);
    }

    static void testForwardModelAdvance(){
        Game game = _prepareGame();

        final int TOTAL_RUNS = WARMUP_ITERATIONS + BENCHMARK_ITERATIONS;

        var games = new Game[TOTAL_RUNS / 10]; //let's hope that it'll always take at least 10 steps to totally loose a game xD
        for(int t=0;t<games.length;t++) games[t] = game.copy();
        var counter = new Object(){public int value = 0;}; //if it doesn't, at least we'll know from crashing on IndexOutOfRange

        long benchmarkedTime = measureExecutionTime(()->{
            var currentGame = games[counter.value];
            currentGame.advanceGame(0);
            if(currentGame.gameOver()) ++counter.value;
            return currentGame;
        });

        System.out.printf("forward model advance: %d ns (%d game clones used)\n", benchmarkedTime, counter.value);
    }


    // public volatile to ensure that each write to this will be unqestionably considered sideeffect
    public static volatile Object _dummyRetval;

    // returns average execution time in nanoseconds
    public static long measureExecutionTime(Benchmark toMeasure){
        long startTime;

        // just to be sure, let System.nanoTime() warmup as well xD
        for(int t=0;t<WARMUP_ITERATIONS;t++){
            startTime = System.nanoTime();
        }

        // warmup
        Object dummyRetval = null;  //store return value just in case, to totally ensure that the function call cannot ever be optimized away
        for(int t=0;t < WARMUP_ITERATIONS;t++){
            dummyRetval = toMeasure.run();
        }


        startTime = System.nanoTime();
        for(int t=0;t < BENCHMARK_ITERATIONS;t++){
            dummyRetval = toMeasure.run();
        }
        long totalDuration = System.nanoTime() - startTime;

        _dummyRetval = dummyRetval; // so that the accumulated return value is really used

        return totalDuration / BENCHMARK_ITERATIONS;

    }

}
