
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import game.Game;
import game.types.state.GameType;
import java.util.HashSet;
import java.util.Set;
import main.FileHandling;
import main.collections.ChunkSet;
import main.collections.FastArrayList;
import util.AI;
import util.Context;
import util.GameLoader;
import util.Move;
import util.Trial;
import util.model.Model;
import util.state.containerState.ContainerState;

/**
 * A simple tutorial that demonstrates a variety of useful methods provided by
 * the Ludii general game system.
 *
 * @author Dennis Soemers
 */
public class Tester {

    public static void main(final String[] args) {

        // Load Knightthrough game
        Game game = GameLoader.loadGameFromName("board/race/reach/Knightthrough.lud");

        ArrayList<Double> param = new ArrayList<>();
        for (double p = 1; p <= 3; p += 0.2) {
            param.add(p);
        }
        
//        param.clear();
//        param.add(3.0);
//        param.add(3.0);
        
        int winCount[] = new int[param.size()];
        System.out.println("size: " + winCount.length);
        boolean versus[][] = new boolean[param.size()][param.size()]; //baris i kolom j true jika agen i mengalahkan agen j

        for (int i = 0; i < param.size() - 1; i++) {
            for (int j = i + 1; j < param.size(); j++) {
                System.gc();
                // to be able to play the game, we need to instantiate "Trial" and "Context" objects
                Trial trial = new Trial(game);
                Context context = new Context(game, trial);
                
                
                //---------------------------------------------------------------------
                // now we're going to have a look at playing a few full games, using AI
                // first, let's instantiate some agents
                final List<AI> agents = new ArrayList<AI>();
                agents.add(null);	// insert null at index 0, because player indices start at 1      
                agents.add(new CustomizedMCTS(Math.sqrt(param.get(i))));
                agents.add(new CustomizedMCTS(Math.sqrt(param.get(j))));

                // number of games we'd like to play
                final int numGames = 1;

                // NOTE: in our following loop through number of games, the different
                // agents are always assigned the same player number. For example,
                // Player 1 will always be random, Player 2 always UCT, Player 3
                // always random, etc.
                //
                // For a fair comparison of playing strength, agent assignments to
                // player numbers should rotate through all possible permutations,
                // to correct for possible first-mover-advantages or disadvantages, etc.
                for (int g = 0; g < numGames; ++g) {
                    // (re)start our game
                    game.start(context);

                    // (re)initialise our agents
                    for (int p = 1; p < agents.size(); ++p) {
                        agents.get(p).initAI(game, p);
                    }

                    // keep going until the game is over
                    while (!context.trial().over()) {
                        // figure out which player is to move
                        final int mover = context.state().mover();

                        // retrieve mover from list of agents
                        final AI agent = agents.get(mover);

                        // ask agent to select a move
                        // we'll give them a search time limit of 0.2 seconds per decision
                        // IMPORTANT: pass a copy of the context, not the context object directly
                        final Move move = agent.selectAction(
                                game,
                                new Context(context),
                                0.5,
                                -1,
                                -1
                        );

                        // apply the chosen move
                        game.apply(context, move);

//                printBoard(context);
//                System.out.println("");
//                System.out.println("---------------------");
//                System.out.println("");
                    }

                    // let's see who won
                    String stat = context.trial().status().toString();
                    if (stat.equals("Player 1 wins.")) {
//                        player1WinCount++;
                        winCount[i]++;
                        versus[i][j] = true;
                        versus[j][i] = false;
                        System.out.println(i + " wins against " + j);
                    } else {
                        winCount[j]++;
                        versus[i][j] = false;
                        versus[j][i] = true;
                        System.out.println(j + " wins against " + i);
                    }
                    
                }
            }
        }
        
        System.out.println("param wincount");
        for (int i = 0; i < winCount.length; i++) {
            System.out.println(param.get(i) + ": " + winCount[i]);
        } System.out.println("");
        
        System.out.println("Match result");
        for (int i = 0; i < versus.length; i++) {
            for (int j = 0; j < versus.length; j++) {
                System.out.print((versus[i][j]?1:0) + " ");
            }
            System.out.println("");
        }
    }

    private static void printBoard(Context context) {
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); // state papan sebelum diapply move
        int states[][] = new int[8][8];
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i % 8, y = i / 8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            states[y][x] = color;
        }
        for (int i = 7; i >= 0; i--) {
            for (int j = 0; j < 8; j++) {
                System.out.print(states[i][j] + " ");
            }
            System.out.println("");
        }
    }

}
