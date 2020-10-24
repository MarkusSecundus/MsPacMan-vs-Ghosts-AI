/*
 * Implementation of "Ms Pac-Man" for the "Ms Pac-Man versus Ghost Team Competition", brought
 * to you by Philipp Rohlfshagen, David Robles and Simon Lucas of the University of Essex.
 * 
 * www.pacman-vs-ghosts.net
 * 
 * Code written by Philipp Rohlfshagen, based on earlier implementations of the game by
 * Simon Lucas and David Robles. 
 * 
 * You may use and distribute this code freely for non-commercial purposes. This notice 
 * needs to be included in all distributions. Deviations from the original should be 
 * clearly documented. We welcome any comments and suggestions regarding the code.
 */
package game.core;

import game.GameConfig;
import controllers.Direction;
import controllers.ghosts.GhostsActions;
import controllers.pacman.PacManAction;

import java.util.*;

/*
 * Simple implementation of Ms Pac-Man. The class Game contains all code relating to the
 * game; the class GameView displays the game. Controllers must implement PacManController
 * and GhostController respectively. The game may be executed using Exec.
 */
public class G implements Game
{	
	public static Random rnd = new Random();
	
	protected GameConfig config;
	
	protected int remainingLevels;
	
	//Static stuff (mazes are immutable - hence static)
	protected static Maze[] mazes=new Maze[NUM_MAZES];			
	
	//Variables (game state):
	protected BitSet pills,powerPills;
	//level-specific
	protected int curMaze,totLevel,levelTime,totalTime,score,ghostEatMultiplier;
	protected boolean gameOver;
	//pac-man-specific
	protected int curPacManLoc,lastPacManDir,livesRemaining;
	protected boolean extraLife;
	//ghosts-specific
    protected int[] curGhostLocs,lastGhostDirs,edibleTimes,lairTimes;

    protected int fruitLoc = -1, fruitType, fruitDir;
    protected int ateFruitTime = 0, ateFruitLoc, ateFruitType;

    static int[] FruitValue = { 100, 200, 500, 700, 1000, 2000, 5000 };

    protected int eatingGhost, eatingTime, eatingScore;
    protected int dyingTime;
	
	/////////////////////////////////////////////////////////////////////////////
	/////////////////  Constructors and Initialisers   //////////////////////////
	/////////////////////////////////////////////////////////////////////////////
	
	//Constructor
	protected G(){}

	//loads the mazes and store them
	protected void init()
	{		
		for(int i=0;i<mazes.length;i++)
			if(mazes[i]==null)
				mazes[i]=new Maze(i);		
	}
	
	//Creates an exact copy of the game
	public Game copy()
	{
		G copy = new G();
		copy.pills=(BitSet)pills.clone();
		copy.powerPills=(BitSet)powerPills.clone();		
		copy.curMaze=curMaze;
		copy.totLevel=totLevel;
		copy.levelTime=levelTime;
		copy.totalTime=totalTime;
		copy.score=score;
		copy.ghostEatMultiplier=ghostEatMultiplier;
		copy.gameOver=gameOver;
		copy.curPacManLoc=curPacManLoc;
		copy.lastPacManDir=lastPacManDir;
		copy.livesRemaining=livesRemaining;
		copy.extraLife=extraLife;
		copy.curGhostLocs=Arrays.copyOf(curGhostLocs,curGhostLocs.length);
		copy.lastGhostDirs=Arrays.copyOf(lastGhostDirs,lastGhostDirs.length);
		copy.edibleTimes=Arrays.copyOf(edibleTimes,edibleTimes.length);
		copy.lairTimes=Arrays.copyOf(lairTimes,lairTimes.length);
        copy.fruitLoc = fruitLoc; copy.fruitType = fruitType;
        copy.fruitDir = fruitDir;
        copy.eatingGhost = eatingGhost; copy.eatingTime = eatingTime;
        copy.eatingScore = eatingScore;
        copy.dyingTime = dyingTime;
        
		return copy;
	}
    
    void setLevel(int level) {
        totLevel = level;

        if (totLevel <= 2)
            curMaze = 0;
        else if (totLevel <= 5)
            curMaze = 1;
        else curMaze = 2 + (totLevel - 6) / 4 % 2;
    }

	//If pac-man has been eaten or a new level has been reached
	protected void reset(boolean newLevel)
	{
		if(newLevel)
		{
			if (remainingLevels > 0) {
				--remainingLevels;
				if (remainingLevels <= 0) {
					gameOver = true;
					return;
				}
			}
			
            setLevel(totLevel + 1);
			levelTime=0;	
			pills=new BitSet(getNumberPills());
			pills.set(0,getNumberPills());
			powerPills=new BitSet(getNumberPowerPills());
			powerPills.set(0,getNumberPowerPills());
			
			if (!config.powerPillsEnabled) {
				powerPills.clear();
			}
			if (config.totalPills < 1) {
				int number = (int)Math.ceil(pills.length() * (1-(config.totalPills > 0 ? config.totalPills : 0)));
				decimatePills(number);
			}
		}
		
		curPacManLoc=getInitialPacPosition();
		lastPacManDir=G.INITIAL_PAC_DIR;
		
        Arrays.fill(curGhostLocs,mazes[curMaze].lairPosition);	
        curGhostLocs[0] = mazes[curMaze].initialGhostsPosition;
		lastGhostDirs=Arrays.copyOf(G.INITIAL_GHOST_DIRS,G.INITIAL_GHOST_DIRS.length);
	
		Arrays.fill(edibleTimes,0);		
		ghostEatMultiplier=1;
        
        lairTimes[0] = 0;
		for(int i=1;i<lairTimes.length;i++)
            lairTimes[i]=(int)(G.LAIR_TIMES[i]*(Math.pow(LAIR_REDUCTION,totLevel - 1)));
            
        eatingTime = dyingTime = 0;
        fruitLoc = -1;
        ateFruitTime = 0;
	}
	
	// Remove 'number' of pills from the maze
	protected void decimatePills(int number) {
		if (number == pills.length()) {
			pills.clear();
		} else {
			List<Integer> pillNodeIndices = new ArrayList<Integer>();
			Node[] graph = mazes[curMaze].graph;
			for (int i = 0; i < graph.length; ++i) {
				if (graph[i].pillIndex >= 0) {
					pillNodeIndices.add(i);
				}
			}
			while (number > 0) {
				int startNodePillIndex = pillNodeIndices.get(G.rnd.nextInt(pillNodeIndices.size()));
				List<Integer> nodeIndices = new ArrayList<Integer>();
				Set<Integer> closedIndices = new HashSet<Integer>();
				nodeIndices.add(startNodePillIndex);
				while (number > 0 && nodeIndices.size() > 0) {
					// CLEAR PILL 
					int nodeIndex = nodeIndices.remove(0);
					int pillIndex = getPillIndex(nodeIndex);				
					pillNodeIndices.remove((Object)nodeIndex);
					closedIndices.add(nodeIndex);
					
					if (pillIndex >= 0 && pills.get(pillIndex)) {
						pills.clear(pillIndex);
						--number;
					}
					
					// CHECK NEIGHBOURS
					int[] neighbours = new int[4];
					int numNeighbours = 0;
					for (Direction dir : Direction.arrows()) {
						int nextNode = getNeighbour(nodeIndex, dir.index);
						if (nextNode >= 0) {
							neighbours[dir.index] = nextNode;
							++numNeighbours;
						} else {
							neighbours[dir.index] = -1;
						}
					}
					if (numNeighbours == 2) {
						// CORRIDOR
						for (int neighbour : neighbours) {
							if (neighbour >= 0 && !closedIndices.contains(neighbour)) {
								nodeIndices.add(neighbour);
							}
						}
					}
				}				
			}
		}
	}
		
	/////////////////////////////////////////////////////////////////////////////
	/////////////////////////////  Game Play   //////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////
    
    boolean actionPaused() {
        if (eatingTime > 0) {
            if (--eatingTime == 0) {
                edibleTimes[eatingGhost]=0;					
                lairTimes[eatingGhost]=(int)(G.COMMON_LAIR_TIME*(Math.pow(G.LAIR_REDUCTION,totLevel - 1)));					
                curGhostLocs[eatingGhost]=mazes[curMaze].lairPosition;
                lastGhostDirs[eatingGhost]=G.INITIAL_GHOST_DIRS[eatingGhost];
            }
            return true;
        }

        if (dyingTime > 0) {
            if (--dyingTime == 0) {
                livesRemaining--;
                if(livesRemaining<=0)
                    gameOver=true;
                else
                    reset(false);
            }
            return true;
        }

        return false;
    }

    void updateFruit() {
        if (ateFruitTime > 0)
            --ateFruitTime;

        if (fruitLoc == -1 && (getNumberPills() - getNumActivePills() == 64 ||
                               getNumActivePills() == 66)) {
            int[] startX = new int[4];
            int count = 0;

            for (Node n : mazes[curMaze].graph)
                if (n.x == 0 || n.x == 108)   // at left or right edge of maze
                    startX[count++] = n.nodeIndex;
            
            if (count == 0)
                throw new RuntimeException("can't find any tunnels");
            
            fruitLoc = startX[rnd.nextInt(count)];
            fruitType = totLevel <= 7 ? totLevel - 1 : rnd.nextInt(7);
            fruitDir = getX(fruitLoc) == 0 ? Game.RIGHT : Game.LEFT;
        } else if (fruitLoc != -1) {
             if (levelTime % 2 == 0) {
                int[] possible = getPossibleDirs(fruitLoc, fruitDir, false);
                fruitDir = possible[rnd.nextInt(possible.length)];
                fruitLoc = getNeighbour(fruitLoc, fruitDir);
                int x = getX(fruitLoc);
                if (x == 0 || x == 108) { // edge of maze
                    fruitLoc = -1;      // fruit is gone
                    return;
                }
             }

             int distance = getPathDistance(curPacManLoc,fruitLoc);
             if (distance <= G.EAT_DISTANCE && distance != -1) {  // ate a fruit
                 score += FruitValue[fruitType];
                 ateFruitTime = 20;
                 ateFruitLoc = fruitLoc;
                 ateFruitType = fruitType;
                 fruitLoc = -1;
             }
        }
    }

	//Central method that advances the game state
	public int[] advanceGame(PacManAction pacMan, GhostsActions ghosts)
	{			
        if (actionPaused())
            return null;

		updatePacMan(pacMan);   	      //move pac-man		
		eatPill();						  //eat a pill
		boolean reverse=eatPowerPill();	  //eat a power pill
		if (ghosts != null) {
			updateGhosts(ghosts, reverse);    //move ghosts
		}
		
		//This is primarily done for the replays as reset (as possibly called by feast()) sets the 
		//last directions to the initial ones, not the ones taken
        int[] replayStep={
            lastPacManDir,
            lastGhostDirs[0],lastGhostDirs[1],lastGhostDirs[2],lastGhostDirs[3],
            curPacManLoc, curGhostLocs[0], curGhostLocs[1], curGhostLocs[2], curGhostLocs[3]};
		
		feast();							//ghosts eat pac-man or vice versa
		
		if (ghosts != null) {
			for(int i=0;i<lairTimes.length && i < ghosts.ghostCount; i++) {
				if(lairTimes[i]>0)
				{
					lairTimes[i]--;
				
					if(lairTimes[i]==0)
						curGhostLocs[i]=mazes[curMaze].initialGhostsPosition;
				}
			}
        }

        updateFruit();
        
		if(!extraLife && score>=EXTRA_LIFE_SCORE)	//award 1 extra life at 10000 points
		{
			extraLife=true;
			livesRemaining++;
		}
	
		totalTime++;
		levelTime++;
		checkLevelState();	//check if level/game is over
		
		return replayStep;
	}
	
	//Central method that advances the game state
	public int[] advanceGameReplay(PacManAction pacMan, GhostsActions ghosts, int pacManLocation, int[] ghostsLocations)
	{
        if (actionPaused())
            return null;
		
		lastPacManDir = pacMan.get().index;
		curPacManLoc = pacManLocation;
		
		eatPill();		  //eat a pill
		eatPowerPill();	  //eat a power pill
		
		if (ghosts != null) {
			for (int i = 0; i < 4; ++i) {
				lastGhostDirs[i] = ghosts.ghost(i).get().index;
				curGhostLocs[i] = ghostsLocations[i];
			}
		}
		
		//This is primarily done for the replays as reset (as possibly called by feast()) sets the 
		//last directions to the initial ones, not the ones taken
        int[] replayStep={ lastPacManDir,
            lastGhostDirs[0],lastGhostDirs[1],lastGhostDirs[2],lastGhostDirs[3],curPacManLoc,
            curGhostLocs[0], curGhostLocs[1], curGhostLocs[2], curGhostLocs[3]};
		
		feast();		//ghosts eat pac-man or vice versa
		
		if (ghosts != null) {
			for(int i=0;i<lairTimes.length;i++) {
				if(lairTimes[i]>0)
				{
					lairTimes[i]--;
				
					if(lairTimes[i]==0)
						curGhostLocs[i]=mazes[curMaze].initialGhostsPosition;
				}
			}
        }
        
        updateFruit();
				
		if(!extraLife && score>=EXTRA_LIFE_SCORE)	//award 1 extra life at 10000 points
		{
			extraLife=true;
			livesRemaining++;
		}
	
		totalTime++;
		levelTime++;
		checkLevelState();	//check if level/game is over
		
		return replayStep;
	}
	
	//Updates the location of Ms Pac-Man
	protected void updatePacMan(PacManAction pacMan)
	{
		int direction = checkPacManDir(pacMan.get().index);
		lastPacManDir = direction;		
		curPacManLoc = getNeighbour(curPacManLoc,direction);
	}
		
	//Checks the direction supplied by the controller and substitutes for a legal one if necessary
	protected int checkPacManDir(int direction)
	{
		int[] neighbours=getPacManNeighbours();
				
		if((direction>3 || direction<0 || neighbours[direction]==-1) && (lastPacManDir>3 || lastPacManDir<0 || neighbours[lastPacManDir]==-1))
			return 4;
		
		if(direction<0 || direction>3)
			direction=lastPacManDir;
		
		if(neighbours[direction]==-1)
			if(neighbours[lastPacManDir]!=-1) 
				direction=lastPacManDir;
			else
			{
				int[] options=getPossiblePacManDirs(true);
				direction=options[0];
			}

		return direction;		
	}
	
	//Updates the locations of the ghosts
	protected void updateGhosts(GhostsActions ghosts,boolean reverse)
	{
		int[] directions = new int[4];
		for (int i = 0; i < ghosts.ghostCount; ++i) {
			directions[i] = ghosts.actions[i].get().index;
		}
		
		for(int i=0;i<ghosts.ghostCount;i++)
		{		
			if(lairTimes[i]==0)
			{
				if(reverse)
				{
					lastGhostDirs[i]=getReverse(lastGhostDirs[i]);
					curGhostLocs[i]=getNeighbour(curGhostLocs[i],lastGhostDirs[i]);
				}
				else if(edibleTimes[i]==0 || edibleTimes[i]%GHOST_SPEED_REDUCTION!=0)
				{
					directions[i]=checkGhostDir(i,directions[i]);
					lastGhostDirs[i]=directions[i];
					curGhostLocs[i]=getNeighbour(curGhostLocs[i],directions[i]);
				}
			}
		}		
	}
	
	//Checks the directions supplied by the controller and substitutes for a legal ones if necessary
	protected int checkGhostDir(int whichGhost,int direction)
	{
		if(direction<0 || direction>3)
			direction=lastGhostDirs[whichGhost];
			
		int[] neighbours=getGhostNeighbours(whichGhost);
			
		if(neighbours[direction]==-1)
		{
			if(neighbours[lastGhostDirs[whichGhost]]!=-1)
				direction=lastGhostDirs[whichGhost];
			else
			{
				int[] options=getPossibleGhostDirs(whichGhost);
				direction=options[0];
			}
		}

		return direction;
	}
		
	//Eats a pill
	protected void eatPill()
	{
		int pillIndex=getPillIndex(curPacManLoc);

		if(pillIndex>=0 && pills.get(pillIndex))
		{
			score+=G.PILL;
			pills.clear(pillIndex);
		}
	}
	
	//Eats a power pill - turns ghosts edible (blue)
	protected boolean eatPowerPill()
	{
		boolean reverse=false;
		int powerPillIndex=getPowerPillIndex(curPacManLoc);
		
		if(powerPillIndex>=0 && powerPills.get(powerPillIndex))
		{
			score+=G.POWER_PILL;
			ghostEatMultiplier=1;
			powerPills.clear(powerPillIndex);
			
			int newEdibleTime=(int)(G.EDIBLE_TIME*(Math.pow(G.EDIBLE_TIME_REDUCTION,totLevel - 1)));
			
			for(int i=0;i<NUM_GHOSTS;i++)
				edibleTimes[i]=newEdibleTime;
			
			reverse=true;
		}
		else if (levelTime>1 && G.rnd.nextDouble() < G.GHOST_REVERSAL) //random ghost reversal
			reverse=true;
		
		return reverse;
	}
	
	//This is where the characters of the game eat one another if possible
	protected void feast()
	{		
		for(int i=0;i<curGhostLocs.length;i++)
		{
			int distance=getPathDistance(curPacManLoc,curGhostLocs[i]);
			
			if(distance<=G.EAT_DISTANCE && distance!=-1)
			{
				if(edibleTimes[i]>0)		//pac-man eats ghost
				{
                    eatingScore = G.GHOST_EAT_SCORE*ghostEatMultiplier;
                    eatingGhost = i;
                    eatingTime = 12;
					score += eatingScore;
                    ghostEatMultiplier*=2;
                    break;  // can eat only one ghost at once
				}
				else						//ghost eats pac-man
                    dyingTime = 20;
			}
		}
		
		for(int i=0;i<edibleTimes.length;i++)
			if(edibleTimes[i]>0)
				edibleTimes[i]--;
	}
	
	//Checks the state of the level/game and advances to the next level or terminates the game
	protected void checkLevelState()
	{
		//if all pills have been eaten or the time is up...
		if((pills.isEmpty() && powerPills.isEmpty()) || levelTime>=LEVEL_LIMIT)
		{
			//award any remaining pills to Ms Pac-Man
			score+=G.PILL*pills.cardinality()+G.POWER_PILL*powerPills.cardinality();			 
			
			//put a cap on the total number of levels played
			if(totLevel==G.MAX_LEVELS)
			{
				gameOver=true;
				return;
			}
			else
				reset(true);
		}		
	}
	
	/////////////////////////////////////////////////////////////////////////////
	///////////////////////////  Getter Methods  ////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////
		
	//Returns the reverse of the direction supplied
	public int getReverse(int direction)
	{
		switch(direction)
		{
			case 0: return 2;
			case 1: return 3;
			case 2: return 0;
			case 3: return 1;
		}
		
		return 4;
	}
	
	//Whether the game is over or not
	public boolean gameOver()
	{
		return gameOver;
	}
	
	//Whether the pill specified is still there
	public boolean checkPill(int nodeIndex)
	{
		return pills.get(nodeIndex);
	}
	
	//Whether the power pill specified is still there
	public boolean checkPowerPill(int nodeIndex)
	{
		return powerPills.get(nodeIndex);
	}
	
	//Returns the neighbours of the node at which Ms Pac-Man currently resides
	public int[] getPacManNeighbours()
	{
		return Arrays.copyOf(mazes[curMaze].graph[curPacManLoc].neighbours,mazes[curMaze].graph[curPacManLoc].neighbours.length);
	}
	
	//Returns the neighbours of the node at which the specified ghost currently resides. NOTE: since ghosts are not allowed to reverse, that
	//neighbour is filtered out. Alternatively use: getNeighbour(), given curGhostLoc[-] for all directions
	public int[] getGhostNeighbours(int whichGhost)
	{
        int[] neighbours=
            Arrays.copyOf(
                mazes[curMaze].graph[curGhostLocs[whichGhost]].neighbours,
                mazes[curMaze].graph[curGhostLocs[whichGhost]].neighbours.length);		
		neighbours[getReverse(lastGhostDirs[whichGhost])]=-1;
		
		return neighbours;
	}
	
	//The current level
	public int getCurLevel()
	{
		return totLevel;
	}
	
	//The current maze (1-4)
	public int getCurMaze()
	{
		return curMaze;
	}
	
	//Current node index of Ms Pac-Man
	public int getCurPacManLoc()
	{
		return curPacManLoc;
	}
	
	//Current node index of Ms Pac-Man
	public int getCurPacManDir()
	{
		return lastPacManDir;
	}
	
	//Lives that remain for Ms Pac-Man
	public int getLivesRemaining()
	{
		return livesRemaining;
	}
	
	//Current node at which the specified ghost resides
	public int getCurGhostLoc(int whichGhost)
	{
		return curGhostLocs[whichGhost];
	}

	//Current direction of the specified ghost
	public int getCurGhostDir(int whichGhost)
	{
		return lastGhostDirs[whichGhost];
	}
	
	//Returns the edible time for the specified ghost
	public int getEdibleTime(int whichGhost)
	{
		return edibleTimes[whichGhost];
	}
	
	//Simpler check to see if a ghost is edible
	public boolean isEdible(int whichGhost)
	{
		return edibleTimes[whichGhost]>0;
    }
    
    public int getEatingTime() { return eatingTime; }

    public int getEatingGhost() { return eatingTime > 0 ? eatingGhost : -1; }

    public int getEatingScore() { return eatingScore; }

	//Returns the score of the game
	public int getScore()
	{
		return score;
	}
	
	//Returns the time of the current level (important with respect to LEVEL_LIMIT)
	public int getLevelTime()
	{
		return levelTime;
	}
	
	//Total time the game has been played for (at most LEVEL_LIMIT*MAX_LEVELS)
	public int getTotalTime()
	{
		return totalTime;
	}
	
	//Total number of pills in the maze
	public int getNumberPills()
	{
		return mazes[curMaze].pillIndices.length;
	}
	
	//Total number of power pills in the maze
	public int getNumberPowerPills()
	{
		return mazes[curMaze].powerPillIndices.length;
	}
	
	//Time left that the specified ghost will spend in the lair
	public int getLairTime(int whichGhost)
	{
		return lairTimes[whichGhost];
	}
	
	//If in lair (getLairTime(-)>0) or if not at junction
	public boolean ghostRequiresAction(int whichGhost)
	{
		return (isJunction(curGhostLocs[whichGhost]) && (edibleTimes[whichGhost]==0 || edibleTimes[whichGhost]%GHOST_SPEED_REDUCTION!=0));
	}
	
	//Returns name of maze: A, B, C, D
	public String getName()
	{
		return mazes[curMaze].name;
	}
				
	//Returns the starting position of Ms PacMan
	public int getInitialPacPosition()
	{
		return mazes[curMaze].initialPacPosition;
	}
	
	//Returns the starting position of the ghosts (i.e., first node AFTER leaving the lair)
	public int getInitialGhostsPosition()
	{
		return mazes[curMaze].initialGhostsPosition;
	}
	
	//Total number of nodes in the graph (i.e., those with pills, power pills and those that are empty)
	public int getNumberOfNodes()
	{
		return mazes[curMaze].graph.length;
	}
		
	//Returns the x coordinate of the specified node
	public int getX(int index)
	{
		return mazes[curMaze].graph[index].x;
	}
	
	//Returns the y coordinate of the specified node
	public int getY(int index)
	{
		return mazes[curMaze].graph[index].y;
	}
	
	//Returns the pill index of the node. If it is -1, the node has no pill. Otherwise one can
	//use the bitset to check whether the pill has already been eaten
	public int getPillIndex(int nodeIndex)
	{
		return mazes[curMaze].graph[nodeIndex].pillIndex;
	}
	
	//Returns the power pill index of the node. If it is -1, the node has no pill. Otherwise one 
	//can use the bitset to check whether the pill has already been eaten
	public int getPowerPillIndex(int nodeIndex)
	{
		return mazes[curMaze].graph[nodeIndex].powerPillIndex;
	}
	
	//Returns the neighbour of node index that corresponds to direction. In the case of neutral, the 
	//same node index is returned
	public int getNeighbour(int nodeIndex,int direction)
	{
		if(direction<0 || direction>3)//this takes care of "neutral"
			return nodeIndex;
		else
			return mazes[curMaze].graph[nodeIndex].neighbours[direction];
	}
		
	//Returns the indices to all the nodes that have pills
	public int[] getPillIndices()
	{
		return Arrays.copyOf(mazes[curMaze].pillIndices,mazes[curMaze].pillIndices.length);
	}
	
	//Returns the indices to all the nodes that have power pills
	public int[] getPowerPillIndices()
	{
		return Arrays.copyOf(mazes[curMaze].powerPillIndices,mazes[curMaze].powerPillIndices.length);
	}
	
	//Returns the indices to all the nodes that are junctions
	public int[] getJunctionIndices()
	{
		return Arrays.copyOf(mazes[curMaze].junctionIndices,mazes[curMaze].junctionIndices.length);
	}
	
	//Checks of a node is a junction
	public boolean isJunction(int nodeIndex)
	{
		return mazes[curMaze].graph[nodeIndex].numNeighbours>2;
	}
	
	//returns the score awarded for the next ghost to be eaten
	public int getNextEdibleGhostScore()
	{
		return G.GHOST_EAT_SCORE*ghostEatMultiplier;
	}
	
	//returns the number of pills still in the maze
	public int getNumActivePills()				
	{
		return pills.cardinality();
	}
	
	//returns the number of power pills still in the maze
	public int getNumActivePowerPills()
	{
		return powerPills.cardinality();
	}
	
	//returns the indices of all active pills in the maze
	public int[] getPillIndicesActive()
	{
		int[] indices=new int[pills.cardinality()];
		
		int index=0;
		
		for(int i=0;i<mazes[curMaze].pillIndices.length;i++)
			if(pills.get(i))
				indices[index++]=mazes[curMaze].pillIndices[i];		
			
		return indices;
	}
	
	//returns the indices of all active power pills in the maze
	public int[] getPowerPillIndicesActive()	
	{
		int[] indices=new int[powerPills.cardinality()];
		
		int index=0;
		
		for(int i=0;i<mazes[curMaze].powerPillIndices.length;i++)
			if(powerPills.get(i))
				indices[index++]=mazes[curMaze].powerPillIndices[i];		
			
		return indices;
	}

	//Returns the number of neighbours of a node: 2, 3 or 4. Exception: lair, which has no neighbours
	public int getNumNeighbours(int nodeIndex)
	{
		return mazes[curMaze].graph[nodeIndex].numNeighbours;
	}
	
	//Returns the actual directions Ms Pac-Man can take
	public int[] getPossiblePacManDirs(boolean includeReverse)
	{
		return getPossibleDirs(curPacManLoc,lastPacManDir,includeReverse);
	}
	
	//Returns the actual directions the specified ghost can take
	public int[] getPossibleGhostDirs(int whichGhost)
	{
		return getPossibleDirs(curGhostLocs[whichGhost],lastGhostDirs[whichGhost],false);		
	}
	
	//Computes the directions to be taken given the current location
	public int[] getPossibleDirs(int curLoc,int curDir,boolean includeReverse)
	{
		int numNeighbours=mazes[curMaze].graph[curLoc].numNeighbours;

		if(numNeighbours==0)
			return new int[0];
		
		int[] nodes=mazes[curMaze].graph[curLoc].neighbours;
		int[] directions;
		
		if(includeReverse || (curDir<0 || curDir>3))
			directions=new int[numNeighbours];
		else
			directions=new int[numNeighbours-1];
		
		int index=0;
		
		for(int i=0;i<nodes.length;i++)
			if(nodes[i]!=-1)
			{
				if(includeReverse || (curDir<0 || curDir>3))
					directions[index++]=i;
				else if(i!=getReverse(curDir))
					directions[index++]=i;
			}

		return directions;
	}
			
	//Returns the direction Pac-Man should take to approach/retreat a target (to) given some distance 
	//measure
	public int getNextPacManDir(int to,boolean closer,DM measure)
	{
		return getNextDir(mazes[curMaze].graph[curPacManLoc].neighbours,to,closer,measure);
	}
	
	//Returns the direction the ghost should take to approach/retreat a target (to) given some distance 
	//measure. Reversals are filtered.
	public int getNextGhostDir(int whichGhost,int to,boolean closer,Game.DM measure)
	{	
		return getNextDir(getGhostNeighbours(whichGhost),to,closer,measure);
	}
	
	//This method returns the direction to take given some options (usually corresponding to the
	//neighbours of the node in question), moving either towards or away (closer in {true, false})
	//using one of the three distance measures.
	private int getNextDir(int[] from,int to,boolean closer,Game.DM measure)
	{
		int dir=-1;

		double min=Integer.MAX_VALUE;
		double max=-Integer.MAX_VALUE;
			
		for(int i=0;i<from.length;i++)
		{
			if(from[i]!=-1)
			{
				double dist=0;
					
				switch(measure)
				{
					case PATH: dist=getPathDistance(from[i],to); break;
					case EUCLID: dist=getEuclideanDistance(from[i],to); break;
					case MANHATTAN: dist=getManhattanDistance(from[i],to); break;
				}
					
				if(closer && dist<min)
				{
					min=dist;
					dir=i;	
				}
				
				if(!closer && dist>max)
				{
					max=dist;
					dir=i;
				}
			}
		}
		
		return dir;
	}
	
	//Returns the PATH distance from any node to any other node
	public int getPathDistance(int from,int to)
	{
		if(from==to)
			return 0;		
		else if(from<to)
			return mazes[curMaze].distances[((to*(to+1))/2)+from];
		else
			return mazes[curMaze].distances[((from*(from+1))/2)+to];
	}
	
	//Returns the EUCLEDIAN distance between two nodes in the current maze.
	public double getEuclideanDistance(int from,int to)
	{
		return Math.sqrt(Math.pow(mazes[curMaze].graph[from].x-mazes[curMaze].graph[to].x,2)+Math.pow(mazes[curMaze].graph[from].y-mazes[curMaze].graph[to].y,2));
	}
	
	
	//Returns the MANHATTAN distance between two nodes in the current maze.
	public int getManhattanDistance(int from,int to)
	{
		return (int)(Math.abs(mazes[curMaze].graph[from].x-mazes[curMaze].graph[to].x)+Math.abs(mazes[curMaze].graph[from].y-mazes[curMaze].graph[to].y));
	}
	
	//Returns the path of adjacent nodes from one node to another, including these nodes
	//E.g., path from a to c might be [a,f,r,t,c]
	public int[] getPath(int from,int to)
	{
		if (from < 0 || to < 0) return new int[0];
		int currentNode=from;
		ArrayList<Integer> path=new ArrayList<Integer>();
		int lastDir;

		while(currentNode!=to)
		{
			path.add(currentNode);
			int[] neighbours=mazes[curMaze].graph[currentNode].neighbours;
			lastDir=getNextDir(neighbours,to,true,G.DM.PATH);
			currentNode=neighbours[lastDir];
		}

		int[] arrayPath=new int[path.size()];

		for(int i=0;i<arrayPath.length;i++)
			arrayPath[i]=path.get(i);

		return arrayPath;
	}
	
	//Similar to getPath(-) but takes into consideration the fact that ghosts may not reverse. Hence the path to be taken
	//may be significantly longer than the shortest available path
	public int[] getGhostPath(int whichGhost,int to)
	{
		if(mazes[curMaze].graph[curGhostLocs[whichGhost]].numNeighbours==0)
			return new int[0];

		int currentNode=curGhostLocs[whichGhost];
		ArrayList<Integer> path=new ArrayList<Integer>();
		int lastDir=lastGhostDirs[whichGhost];

		while(currentNode!=to)
		{
			path.add(currentNode);
			int[] neighbours=getGhostNeighbours(currentNode,lastDir);
			lastDir=getNextDir(neighbours,to,true,G.DM.PATH);
			currentNode=neighbours[lastDir];
		}

		int[] arrayPath=new int[path.size()];

		for(int i=0;i<arrayPath.length;i++)
			arrayPath[i]=path.get(i);

		return arrayPath;
	}
	
	//Returns the node from 'targets' that is closest/farthest from the node 'from' given the distance measure specified
	public int getTarget(int from,int[] targets,boolean nearest,Game.DM measure)
	{
		int target=-1;

		double min=Integer.MAX_VALUE;
		double max=-Integer.MAX_VALUE;
		
		for(int i=0;i<targets.length;i++)
		{				
			double dist=0;
			
			switch(measure)
			{
				case PATH: dist=getPathDistance(targets[i],from); break;
				case EUCLID: dist=getEuclideanDistance(targets[i],from); break;
				case MANHATTAN: dist=getManhattanDistance(targets[i],from); break;
			}
					
			if(nearest && dist<min)
			{
				min=dist;
				target=targets[i];	
			}
				
			if(!nearest && dist>max)
			{
				max=dist;
				target=targets[i];
			}
		}
		
		return target;
	}
	
	//Returns the target closes from the position of the ghost, considering that reversals are not allowed
	public int getGhostTarget(int whichGhost,int[] targets,boolean nearest)
	{
		int target=-1;

		double min=Integer.MAX_VALUE;
		double max=-Integer.MAX_VALUE;
		
		for(int i=0;i<targets.length;i++)
		{				
			double dist=getGhostPathDistance(whichGhost,targets[i]);
					
			if(nearest && dist<min)
			{
				min=dist;
				target=targets[i];	
			}
				
			if(!nearest && dist>max)
			{
				max=dist;
				target=targets[i];
			}
		}
		
		return target;
	}
	
	//Returns the path distance for a particular ghost: takes into account the fact that ghosts may not reverse
	public int getGhostPathDistance(int whichGhost,int to)
	{
		return getGhostPath(whichGhost,to).length;
	}
	
	//Returns the neighbours of a node with the one correspodining to the reverse of direction being deleted (i.e., =-1)
	private int[] getGhostNeighbours(int node,int lastDirection)
	{
		int[] neighbours=Arrays.copyOf(mazes[curMaze].graph[node].neighbours,mazes[curMaze].graph[node].neighbours.length);		
		neighbours[getReverse(lastDirection)]=-1;
		
		return neighbours;
    }
    
    public int getFruitLoc() { return fruitLoc; }

    public int getFruitType() { return fruitLoc == -1 ? -1 : fruitType; }

    public int getFruitValue() { return fruitLoc == -1 ? 0 : FruitValue[fruitType]; }
}
