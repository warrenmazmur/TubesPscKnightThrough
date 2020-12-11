
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import main.collections.FastArrayList;
import util.AI;
import util.Context;
import util.Move;
import util.state.containerState.ContainerState;
import utils.AIUtils;

/**
 * Agen cerdas berbasis Monte Carlo Tree search dengan enhancement 
 * permainan untuk Knightthrough.
 *
 * @author Warren Mazmur
 * @author Jiang Han
 * diadaptasi dari https://github.com/Ludeme/LudiiExampleAI/blob/master/src/mcts/ExampleUCT.java
 */
public class MctsKnightThrough extends AI {
    /**
     * Indeks pemain untuk agen ini
     */
    protected int player = -1;
    
    protected static Map<Node, Node> visited;
    
    /**
     * Konstruktor
     */
    public MctsKnightThrough() {
        visited = new HashMap<>();
        this.friendlyName = "Ujang";
    }

    /**
     * Method untuk memilik move yang akan dijalankan pada state tertentu
     * @param game Game engine dari Ludii
     * @param context Konteks yang permainan saat ini (statenya, aturannya, available move, dll)
     * @param maxSeconds Waktu maksimal yang dianjurkan untuk melakukan komputasi
     * @param maxIterations Iterasi maksimal yang dianjurkan untuk melakukan komputasi
     * @param maxDepth Kedalaman pencarian pada tree yang dianjurkan 
     * @return Object Move yang dipilih oleh agen
     */
    @Override
    public Move selectAction(
            final Game game,
            final Context context,
            final double maxSeconds,
            final int maxIterations,
            final int maxDepth
    ) {
        // Membuat node root
        Node root = new Node(null, context);
        // Jika root sudah pernah divisit, 
        if (visited.containsKey(root)){
            // kita memakai data dari Node yang sudah pernah divisit tersebut
            root = visited.get(root);
        } else {
            // Jika belum divisit, kita memasukkan node baru ke dalam hashmap.
            visited.put(root, root);
        }
        
        // menghapus parent dari root supaya tidak bisa kembali ke state
        // yang sudah tidak bisa divisit
        root.parent = null;

        // We'll respect any limitations on max seconds and max iterations (don't care about max depth)
        final long stopTime = (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;

        int numIterations = 0;

        // Our main loop through MCTS iterations
        while (numIterations < maxIts
                && // Respect iteration limit
                System.currentTimeMillis() < stopTime
                && // Respect time limit
                !wantsInterrupt // Respect GUI user clicking the pause button
                ) {
            // Start in root node
            Node current = root;

            // Traverse tree
            while (true) {
                if (current.context.trial().over()) {
                    // We've reached a terminal state
                    break;
                }

                current = select(current);

                if (current.visitCount == 0) {
                    // We've expanded a new node, time for playout!
                    break;
                }
            }

            Context contextEnd = current.context;
            //TODO(implement heuristic & evaluation function to replace random playout)
            if (!contextEnd.trial().over()) {
                // Run a playout if we don't already have a terminal game state in node
                contextEnd = new Context(contextEnd);
                game.playout(
                        contextEnd,
                        null,
                        -1.0,
                        null,
                        null,
                        0,
                        -1,
                        0.f,
                        ThreadLocalRandom.current()
                );
            }

            // This computes utilities for all players at the of the playout,
            // which will all be values in [-1.0, 1.0]
            final double[] utilities = AIUtils.utilities(contextEnd);

            // Backpropagate utilities through the tree
            while (current != null) {
                current.visitCount += 1;
                for (int p = 1; p <= game.players().count(); ++p) {
                    current.scoreSums[p] += utilities[p];
                }
                current = current.parent;
            }

            // Increment iteration count
            ++numIterations;
        }

        // Return the move we wish to play
        return finalMoveSelection(root);
    }

    /**
     * Selects child of the given "current" node according to UCB1 equation.
     * This method also implements the "Expansion" phase of MCTS, and creates a
     * new node if the given current node has unexpanded moves.
     *
     * @param current
     * @return Selected node (if it has 0 visits, it will be a newly-expanded
     * node).
     */
    public static Node select(final Node current) {
        if (!current.unexpandedMoves.isEmpty()) {
            // randomly select an unexpanded move
            final Move move = current.unexpandedMoves.remove(
                    ThreadLocalRandom.current().nextInt(current.unexpandedMoves.size()));

            // create a copy of context
            final Context context = new Context(current.context);

            // apply the move
            context.game().apply(context, move);

            // create new node and return it
            Node newNode = new Node(current, context);
            if (visited.containsKey(newNode)){
                visited.get(newNode).parent = current;
            } else {
                visited.put(newNode, newNode);
            }
            
            // catet move dari parent ke children.
            current.moveToChildren.put(newNode, move);
            return newNode;
        }

        // use UCB1 equation to select from all children, with random tie-breaking
        Node bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        final double twoParentLog = 2.0 * Math.log(Math.max(1, current.visitCount));
        int numBestFound = 0;

        final int numChildren = current.children.size();
        final int mover = current.context.state().mover();

        for (int i = 0; i < numChildren; ++i) {
            final Node child = current.children.get(i);
            final double exploit = child.scoreSums[mover] / child.visitCount;
            final double explore = Math.sqrt(twoParentLog / child.visitCount);

            final double ucb1Value = exploit + explore;

            if (ucb1Value > bestValue) {
                bestValue = ucb1Value;
                bestChild = child;
                numBestFound = 1;
            } else if (ucb1Value == bestValue
                    && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                // this case implements random tie-breaking
                bestChild = child;
            }
        }

        return bestChild;
    }

    /**
     * Selects the move we wish to play using the "Robust Child" strategy
     * (meaning that we play the move leading to the child of the root node with
     * the highest visit count).
     *
     * @param rootNode
     * @return
     */
    public static Move finalMoveSelection(final Node rootNode) {
        Node bestChild = null;
        int bestVisitCount = Integer.MIN_VALUE;
        int numBestFound = 0;
        
        final int numChildren = rootNode.children.size();
        
        for (int i = 0; i < numChildren; ++i) {
            final Node child = rootNode.children.get(i);
            final int visitCount = child.visitCount;

            if (visitCount > bestVisitCount) {
                bestVisitCount = visitCount;
                bestChild = child;
                numBestFound = 1;
            } else if (visitCount == bestVisitCount
                    && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                // this case implements random tie-breaking
                bestChild = child;
            }
        }

        return rootNode.moveToChildren.get(bestChild);
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;
    }

    @Override
    public boolean supportsGame(final Game game) {
        if (game.isStochasticGame()) {
            return false;
        }

        if (!game.isAlternatingMoveGame()) {
            return false;
        }

        return true;
    }

    //-------------------------------------------------------------------------
    /**
     * Inner class for nodes used by example UCT
     *
     * @author Dennis Soemers
     */
    private static class Node {

        /**
         * Our parent node
         */
        private Node parent;

        /**
         * This objects contains the game state for this node (this is why we
         * don't support stochastic games)
         */
        private final Context context;

        /**
         * Visit count for this node
         */
        private int visitCount = 0;

        /**
         * For every player, sum of utilities / scores back propagated through
         * this node
         */
        private final double[] scoreSums;

        /**
         * Child nodes
         */
        private final List<Node> children = new ArrayList<Node>();
        private final Map<Node, Move> moveToChildren = new HashMap<>();

        /**
         * List of moves for which we did not yet create a child node
         */
        private final FastArrayList<Move> unexpandedMoves;

        /**
         * Constructor
         *
         * @param parent
         * @param moveFromParent
         * @param context
         */
        public Node(final Node parent, final Context context) {
            this.parent = parent;
            this.context = context;
            final Game game = context.game();
            scoreSums = new double[game.players().count() + 1];

            // For simplicity, we just take ALL legal moves. 
            // This means we do not support simultaneous-move games.
            unexpandedMoves = new FastArrayList<>(game.moves(context).moves());

            if (parent != null) {
                parent.children.add(this);
            }
        }

        @Override
        public int hashCode() {
            return context.state().containerStates()[0].cloneWhoCell().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Node){
                return context.state().containerStates()[0].cloneWhatCell().toString().equals(((Node)obj).context.state().containerStates()[0].cloneWhatCell().toString());
            } else {
                return false;
            }
        }

        
    }

    //-------------------------------------------------------------------------
}
