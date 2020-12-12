

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import game.Game;
import game.types.state.GameType;
import main.FileHandling;
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

        // to be able to play the game, we need to instantiate "Trial" and "Context" objects
        Trial trial = new Trial(game);
        Context context = new Context(game, trial);

        //---------------------------------------------------------------------
        // now we're going to have a look at playing a few full games, using AI
        // first, let's instantiate some agents
        final List<AI> agents = new ArrayList<AI>();
        agents.add(null);	// insert null at index 0, because player indices start at 1
        agents.add(new MCTSTreeReuse());
        agents.add(new MCTSTreeReuse());
        

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
        for (int i = 0; i < numGames; ++i) {
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
                        0.2,
                        -1,
                        -1
                );

                // apply the chosen move
                game.apply(context, move);
            }
            
            // let's see who won
            System.out.println(context.trial().status());
            for (final ContainerState containerState : context.state().containerStates()) {
                System.out.println("last state = " + containerState.cloneWhoCell().toChunkString());
            }
        }
    }

}
