
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import main.collections.ChunkSet;
import main.collections.FastArrayList;
import util.AI;
import util.Context;
import util.Move;
import util.action.Action;
import util.state.containerState.ContainerState;
import utils.AIUtils;

/**
 * Agen cerdas berbasis Monte Carlo Tree search dengan enhancement 
 * permainan untuk Knightthrough.
 * Diadaptasi dari https://github.com/Ludeme/LudiiExampleAI/blob/master/src/mcts/ExampleUCT.java
 *
 * @author Warren Mazmur
 * @author Jiang Han
 * 
 */
public class MCTSTreeReuse extends AI {
    /**
     * Indeks pemain untuk agen ini
     */
    protected int player = -1;
    
//    protected static Map<Node, Node> visited;
    
    protected static Map<String, Node> visited;
    protected static Node sentinel = null;
    public static class WeightedMove implements Comparable<WeightedMove>{
        public Move move;
        public double heuristicValue;

        public WeightedMove(Move move, double heuristicValue) {
            this.move = move;
            this.heuristicValue = heuristicValue;
        }

        @Override
        public int compareTo(WeightedMove o) {
            if(this.heuristicValue > o.heuristicValue) return -1;
            else if(this.heuristicValue == o.heuristicValue) return 0;
            else return 1;
        }
    }
    
    /**
     * Konstruktor
     */
    public MCTSTreeReuse() {
        visited = new HashMap<>();
        Node sentinel = null;
        this.friendlyName = "Ujang x Udin v.7H";
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
//        game.
        // Membuat node root
        Node root = new Node(sentinel, null, context);
        String rHash = root.nodeHash();
        if (visited.containsKey(rHash)){
            root = visited.get(rHash);
            System.out.println("Gotten");
        } else {
            System.out.println("missed");
        }        

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
//                game.playout(
//                        contextEnd,
//                        null,
//                        -1.0,
//                        null,
//                        null,
//                        0,
//                        -1,
//                        0.f,
//                        ThreadLocalRandom.current()
//                );
//                playoutRandom(context,game);
                playoutHeuristic(context, game);
                
            }

            // This computes utilities for all players at the of the playout,
            // which will all be values in [-1.0, 1.0]
            final double[] utilities = AIUtils.utilities(contextEnd);

            // Backpropagate utilities through the tree
            while (current != sentinel) {
                current.visitCount += 1;
                for (int p = 1; p <= game.players().count(); ++p) {
                    current.scoreSums[p] += utilities[p];
                }
                current = current.parent;
            }

            // Increment iteration count
            ++numIterations;
        }
        
        Move finalMove = null;

        //cek fixed move
        final int mover = context.state().mover(); // player yang mendapat giliran saat ini
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); // state papan sebelum diapply move
        Set<Integer> criticalCell = new HashSet();
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(mover == 1 ) {
                if(color == 2 && (y == 1 || y == 2)) {
                    criticalCell.add(i);
                }
            }
            else {
                if(color == 1 && (y == 5 || y == 6)) {
                    criticalCell.add(i);
                }
            }
        }
        System.out.println("Player " + mover);
        System.out.println("set size: " + criticalCell.size());
        for (Integer i : criticalCell){
            System.out.println("(" + (i/8) + ", " + (i%8) +")");
        }
        
        final FastArrayList<Move> legalMoves = game.moves(context).moves();
        moveSearch: for (Move move : legalMoves){
            Context ctxCopy = new Context(context);
            game.apply(ctxCopy, move);
//            System.out.println("======================");
//            printBoard(ctxCopy);
//            System.out.println("======================");
            
            ChunkSet chunksCopy = ctxCopy.state().containerStates()[0].cloneWhoCell();
            for (int i = 0; i < 64; i++) {
                int color = chunksCopy.getChunk(i); // nomor player yang menempati posisi i
                int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
                if (mover == 1){
                    //cek menang
                    if (y==7 && color == 1){
                        finalMove = move;
                        break moveSearch;
                    }
                    //cek defensive
//                    System.out.println("y: " + (i/8));
//                    System.out.println("x: " + (i%8));
//                    System.out.println("color: " + color);
                    if (color == 1 && criticalCell.contains(i)){
                        System.out.println("Johan");
                        finalMove = move;
                        continue moveSearch;
                    }
                } else { //mover == 2
                    //cek menang
                    if (y==0 && color == 2){
                        finalMove = move;
                        break moveSearch;
                    }
                    if (color == 2 && criticalCell.contains(i)){
                        finalMove = move;
                        continue moveSearch;
                    }
                }
            }
        }

        System.out.println(mover + " final move: " + finalMove);
        // Return the move we wish to play
        if (finalMove == null){
            finalMove = finalMoveSelection(root, player);
        }
        
        game.apply(context, finalMove);
        sentinel = new Node(root, finalMove, context);
        
        return finalMove;
    }
    
    /**
     * 
     */
    public static void playoutRandom(Context context, Game game) {
        while(!context.trial().over()) {
            final FastArrayList<Move> legalMoves = game.moves(context).moves();
            Random rand = new Random();
            Move nextMove = legalMoves.get(rand.nextInt(legalMoves.size()));
            game.apply(context, nextMove);
        }
        
    }
    
    public static void playoutHeuristic(Context context, Game game) {
        while(!context.trial().over()) {
            final FastArrayList<Move> legalMoves = game.moves(context).moves();
            Random rand = ThreadLocalRandom.current();
            WeightedMove listMove[] = new WeightedMove[legalMoves.size()];
            for (int i = 0 ; i < legalMoves.size() ; i++) {
                double heuristicValue = heuristicFunction(context, game, legalMoves.get(i));
                listMove[i] = new WeightedMove(legalMoves.get(i), heuristicValue);
            }
            
            Arrays.sort(listMove);
            
            Move nextMove;
            if(listMove[0].heuristicValue == 1000000 || listMove[0].heuristicValue == 999999) {
                nextMove = listMove[0].move;
//                isFixedNextMove = true;
//                fixedNextMove = listMove[0].move;
                break;
            }
            else if (listMove[0].heuristicValue != -1000000){
                FastArrayList<WeightedMove> roulette = new FastArrayList<>();
                double minValue = listMove[0].heuristicValue;
                double totalValue = 0;
                for (int i = 0; i < listMove.length; i++) {
                    if(listMove[i].heuristicValue != -1000000) {
                        roulette.add(listMove[i]);
                        minValue = Math.min(minValue, listMove[i].heuristicValue);
                        totalValue += listMove[i].heuristicValue;
                    } else {
                        break;
                    }
                }
                
                if(minValue < 0) {
                    for (WeightedMove wm : roulette) {
                        wm.heuristicValue += Math.abs(minValue);
                    }
                    totalValue += roulette.size() * Math.abs(minValue);
                }
                
                double selectedValue = ThreadLocalRandom.current().nextDouble(0, totalValue);
                
                double lowerBound = 0;
                double upperBound = 0;
                
                nextMove = roulette.get(0).move; // pasti dioverwrite
                for (int i = 0; i < roulette.size(); i++) {
                    upperBound += roulette.get(i).heuristicValue;
                    if(selectedValue >= lowerBound && selectedValue <= upperBound) {
                        nextMove = roulette.get(i).move;
                        break;
                    }
                    lowerBound = upperBound;
                }
                
                
            }else {
                nextMove = legalMoves.get(rand.nextInt(legalMoves.size()));
            }
            
            game.apply(context, nextMove);
            
        }
    }
    
    public static double heuristicFunction(Context context, Game game, Move move) {
        final int mover = context.state().mover(); // player yang mendapat giliran saat ini
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); // state papan sebelum diapply move
        Set<Integer> criticalCell = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(mover == 1 ) {
                if(color == 2 && (y == 1 || y == 2)) {
                    criticalCell.add(i);
                }
            }
            else {
                if(color == 1 && (y == 5 || y == 6)) {
                    criticalCell.add(i);
                }
            }
        }
        
        Context context2 = new Context(context);
        game.apply(context2, move);
        ChunkSet chunks = context2.state().containerStates()[0].cloneWhoCell();
        
        FastArrayList<Integer> pos1 = new FastArrayList<>(); // ArrayList yang menampung posisi-posisi dari kuda player 1
        FastArrayList<Integer> pos2 = new FastArrayList<>(); // ArrayList yang menampung posisi-posisi dari kuda player 2
        
        int state[][] = new int[8][8]; // representasi papan dari state saat ini
        double heuristicValue = 0;
        
        
        boolean fixKalah = false;
        for (int i = 0; i < 64; i++) {
            int color = chunks.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(color == 1) { // putih
                pos1.add(i);
                if(y == 7 && mover == 1){ // posisi goal dari player 1
                    return 1000000; // heuristic value diset Infinity agar move ini pasti terpilih
                }
                else if(mover == 1 && (y == 5 || y == 6)) {
                    if(criticalCell.contains(i)) {
                        return 999999;
                    }
                }
                else if(y >= 5 && mover == 2) {
                    fixKalah = true;
                }
                
            }
            else { // hitam
                pos2.add(i);
                if(y == 0){ // posisi goal dari player 2
                    return 1000000; // heuristic value diset Infinity agar move ini pasti terpilih
                }
                else if(mover == 2 && (y == 1 || y == 2)) {
                    if(criticalCell.contains(i)) {
                        return 999999;
                    }
                }
                else if(y <= 2 && mover == 1) {
                    fixKalah = true;
                }
            }
            
            state[y][x] = color; // update papan
        }
        
        if (fixKalah) return -1000000;
        
        if(mover == 1) { // giliran saat ini adalah player 1
            for (int i : pos1) {
                int x = i%8, y = i/8;
                
                // hitung total setiap kuda sekutu dijaga oleh berapa kuda sekutu
                if(x-1 >= 0 && y-2 >= 0 && state[y-2][x-1] == mover) {
                    heuristicValue++;
                }
                if(x-2 >= 0 && y-1 >= 0 && state[y-1][x-2] == mover) {
                    heuristicValue++;
                }
                if(x+1 < 8 && y-2 >= 0 && state[y-2][x+1] == mover) {
                    heuristicValue++;
                }
                if(x+2 < 8 && y-1 >= 0 && state[y-1][x+2] == mover) {
                    heuristicValue++;
                }
                
                // hitung total setiap kuda sekutu terancam oleh berapa kuda lawan
                if(x-1 >= 0 && y+2 < 8 && state[y+2][x-1] != mover) {
                    heuristicValue--;
                }
                if(x-2 >= 0 && y+1 < 8 && state[y+1][x-2] != mover) {
                    heuristicValue--;
                }
                if(x+1 < 8 && y+2 < 8 && state[y+2][x+1] != mover) {
                    heuristicValue--;
                }
                if(x+2 < 8 && y+1 < 8 && state[y+1][x+2] != mover) {
                    heuristicValue--;
                }

            }
            
            for (int i : pos2) {
                int x = i%8, y = i/8;
                
            }
        }
        else { // giliran saat ini adalah player 2
            for (int i : pos2) {
                int x = i%8, y = i/8;
                
                // hitung total setiap kuda sekutu dijaga oleh berapa kuda sekutu
                if(x-1 >= 0 && y+2 < 8 && state[y+2][x-1] == mover) {
                    heuristicValue++;
                }
                if(x-2 >= 0 && y+1 < 8 && state[y+1][x-2] == mover) {
                    heuristicValue++;
                }
                if(x+1 < 8 && y+2 < 8 && state[y+2][x+1] == mover) {
                    heuristicValue++;
                }
                if(x+2 < 8 && y+1 < 8 && state[y+1][x+2] == mover) {
                    heuristicValue++;
                }
                
                // hitung total setiap kuda sekutu terancam oleh berapa kuda lawan
                if(x-1 >= 0 && y-2 >= 0 && state[y-2][x-1] != mover) {
                    heuristicValue--;
                }
                if(x-2 >= 0 && y-1 >= 0 && state[y-1][x-2] != mover) {
                    heuristicValue--;
                }
                if(x+1 < 8 && y-2 >= 0 && state[y-2][x+1] != mover) {
                    heuristicValue--;
                }
                if(x+2 < 8 && y-1 >= 0 && state[y-1][x+2] != mover) {
                    heuristicValue--;
                }
            }
        }
       
        return heuristicValue;
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
            Node newNode = new Node(current, move, context);

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
     * Selects the move we wish to play using the "Robust Child" strategy __ coba pake max value strategy
     * (meaning that we play the move leading to the child of the root node with
     * the highest visit count).
     *
     * @param rootNode
     * @return
     */
    public static Move finalMoveSelection(final Node rootNode, int playerId) {
        
//        if(isFixedNextMove) return fixedNextMove;
        
        Node bestChild = null;
        double bestValue = Integer.MIN_VALUE;
        int numBestFound = 0;
        
        final int numChildren = rootNode.children.size();
        
        for (int i = 0; i < numChildren; ++i) {
            final Node child = rootNode.children.get(i);
            final double value = child.scoreSums[playerId];

            if (value > bestValue) {
                bestValue = value;
                bestChild = child;
                numBestFound = 1;
            } else if (value == bestValue
                    && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                // this case implements random tie-breaking
                bestChild = child;
            }
        }

        return bestChild.moveFromParent;
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
        private final Move moveFromParent;

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
        public Node(final Node parent, final Move moveFromParent, final Context context) {
            this.parent = parent;
            this.moveFromParent = moveFromParent;
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
        
        public String nodeHash() {
            if (parent==null){
                return (context.state().containerStates()[0].cloneWhoCell().toString() + "null");
            } else {
                return (context.state().containerStates()[0].cloneWhoCell().toString() + parent.context.state().containerStates()[0].cloneWhoCell().toString());
            }
        }

//        @Override
//        public boolean equals(Object obj) {
//            if (obj instanceof Node){
//                Node nObj = (Node) obj;
//                return this.parent == nObj.parent && context.state().containerStates()[0].cloneWhatCell().toString().equals((nObj).context.state().containerStates()[0].cloneWhatCell().toString());
//            } else {
//                return false;
//            }
//        }

        
    }

    //-------------------------------------------------------------------------
    
    
    private static void printBoard(Context context){
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); // state papan sebelum diapply move
        int states[][] = new int[8][8];
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            states[y][x] = color;
        }
        for (int i = 7; i>=0; i--){
            for (int j = 0; j < 8; j++) {
                System.out.print(states[i][j] + " ");
            }
            System.out.println("");
        }
    }
}
