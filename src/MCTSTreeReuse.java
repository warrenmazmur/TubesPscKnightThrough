
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
    /**
     * flag untuk debugger
     */
    public boolean debug = false;
    /**
     * HashMap untuk menyimpan node yang sudah pernah diexpand
     */
    protected static Map<String, Node> visited;
    /**
     * pending
     */
    protected static Node sentinel = null;
    
    /**
     * parameter eksplorasi untuk selection policy dengan UCB1
     */
    public static double eksplorasi;
    
    /**
     * class untuk membungkus objek Move dengan nilai heuristiknya.
     */
    public static class WeightedMove implements Comparable<WeightedMove>{
        public Move move;
        public double heuristicValue;

        /**
         * Konstruktor
         * @param move Move yang disimpan
         * @param heuristicValue nilai heuristik dari move tersebut
         */
        public WeightedMove(Move move, double heuristicValue) {
            this.move = move;
            this.heuristicValue = heuristicValue;
        }

        /**
         * Method untuk membandingan nilai WeightedMove supaya bisa diurutkan
         * @param o Objek WeightedMove yang ingin dibandingkan
         * @return -1 jika nilai objek ini lebih kecil,
         *          0 bila sama, 
         *          1 bisa lebih besar
         */
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
        visited = new HashMap<>(); //menggunakan hashmap supaya kompleksitasnya O(1)
        Node sentinel = null; //pending
        this.friendlyName = "Ujang x Udin v.7i"; //nama yang dapat ditampilkan pada GUI Ludii
        eksplorasi = Math.sqrt(2);
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
        Node root = new Node(sentinel, null, context);
        String rHash = root.nodeHash(); //menghitung nilai hash dari node root
        
        System.out.println("Visited size: " + visited.size());
        //Mereuse node jika node root pernah diexpand
        if (visited.containsKey(rHash)){
            root = visited.get(rHash);
            System.out.println("Gotten");
        } else {
            System.out.println("missed");
            visited.put(rHash, root);
        }        

        // Menghitung batas waktu untuk berhenti melakukan simulasi
        final long stopTime = (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        // Menghitung batas iterasi untuk berhenti melakukan simulasi
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;
        //variabel untuk menghitung jumlah iterasi yyang sudah dilakukan
        int numIterations = 0;

        // Simulasi MCTS
        while (numIterations < maxIts
                && // pengecekan batas iterasi
                System.currentTimeMillis() < stopTime
                && // pengecekan batas waktu
                !wantsInterrupt // memeriksa apakah user ingin mem-pause jalannya permainan
                ) {
            // Mulai penelurusan dari node root
            Node current = root;

            // menelusuri pohon
            while (true) {
                if (current.context.trial().over()) {
                    // berhenti karena telah mencapai terminal state
                    break;
                }

                //memilih node berikutnya berdasarkan selection policy
                current = select(current);

                if (current.visitCount == 0) {
                    // Kita sudah menemukan node baru, perlu dilakukan play-out
                    break;
                }
            }
            
            Context contextEnd = current.context; //mengambil context dari Node akhir
            //Melakukan playout bisa belum mencapai terminal state
            if (!contextEnd.trial().over()) {
                // mengcopy context supaya context asli tidak berubah
                contextEnd = new Context(contextEnd);
                //Bekas play-out random dari contoh
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
                //melakukan playout dengan heuristik dan evaluation function
                playoutHeuristic(context, 20);
            }
            //array untuk menyimpan nilai utility bagi setiap player dari hasil play-out
            final double[] utilities;
            if(!contextEnd.trial().over()) {
                // pakai evaluation function sebab simuulasi dipotong
                utilities = evaluationFunction(contextEnd);
            }else {
                // bila sudah mencapai terminal state, menghitung utility pemain dengan method bawaan Ludii
                utilities = AIUtils.utilities(contextEnd);
            }

            // mencatat nilai utility dari Node leaf sampe ke root (backpropagate)
            while (current != sentinel) {
                current.visitCount += 1; //menambah jumlah kunjungan pada node
                //menambah nilai utility untuk setiap pemain
                for (int p = 1; p <= game.players().count(); ++p) {
                    current.scoreSums[p] += utilities[p];
                }
                current = current.parent; //memindahkan node ke parentnya
            }

            // Menambah jumlah iterarsi saat ini
            ++numIterations;
        }
        
        Move finalMove = null; //variable untuk menyimpan move yang akan dipilih saat ini

        // memeriksa bila ada move yang mutlak harus diambil
        // mengambil nomor player yang mendapat giliran saat ini
        final int mover = context.state().mover(); 
        // state papan sebelum diapply move
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); 
        // set untuk menyimpan cell-cell yang "kritis"
        Set<Integer> criticalCell = new HashSet();
        // memeriksa setiap cell
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(mover == 1 ) {//jika saat ini giliran player putih
                if(color == 2 && (y == 1 || y == 2)) {
                    //Tambahkan cell kritis karena ada kuda musuh yang bisa menang
                    //dalam 1 langkah
                    criticalCell.add(i);
                }
            }
            else { //jika saat ini giliran player hitam
                if(color == 1 && (y == 5 || y == 6)) {
                    //Tambahkan cell kritis karena ada kuda musuh yang bisa menang
                    //dalam 1 langkah
                    criticalCell.add(i);
                }
            }
        }
        
        if (debug){ //debugger
            System.out.println("Player " + mover);
            System.out.println("set size: " + criticalCell.size());
            for (Integer i : criticalCell){
                System.out.println("(" + (i/8) + ", " + (i%8) +")");
            }
        }
        
        //mengambil list semua Move yang bisa dilakukan
        final FastArrayList<Move> legalMoves = game.moves(context).moves();
        moveSearch: for (Move move : legalMoves){ //untuk setiap move
            Context ctxCopy = new Context(context); //copy contextnya
            game.apply(ctxCopy, move); //apply move supaya kita mendapat context berikutnya
            if (debug){  //debugger
                System.out.println("======================");
                printBoard(ctxCopy);
                System.out.println("======================");
            }
            //mengambil game state berikutnya
            ChunkSet chunksCopy = ctxCopy.state().containerStates()[0].cloneWhoCell();
            for (int i = 0; i < 64; i++) { //untuk setiap cell pada papan
                int color = chunksCopy.getChunk(i); // nomor player yang menempati posisi i
                int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
                if (mover == 1){//jika saat ini giliran player putih
                    //cek apakah kita (player putih) bisa menang dalam 1 move
                    if (y==7 && color == 1){
                        //jika bisa, move ini mutlak harus diambil
                        finalMove = move;
                        break moveSearch; //berhenti mengecek
                    }
                    //cek apakah musuh (player hitam) bisa menang dalam 1 move
                    if (debug){ //debugger
                        System.out.println("y: " + (i/8));
                        System.out.println("x: " + (i%8));
                        System.out.println("color: " + color);
                    }
                    //Apakah move ini memakan kuda musuh yang ada pada cell kritis?
                    if (color == 1 && criticalCell.contains(i)){
                        //jika ya, move mutlak diambil. Jika tidak, kita akan kalah
                        finalMove = move;
                        //teruskan pengecekan, siapa tau ada move yang bisa membuat kita menang
                        continue moveSearch;
                    }
                } else { //jika saat ini giliran player hitam
                    //cek apakah kita (player hitam) bisa menang dalam 1 move
                    if (y==0 && color == 2){
                        //jika bisa, move ini mutlak harus diambil
                        finalMove = move;
                        break moveSearch; //hentikan pengecekan
                    }
                    //cek apakah musuh (player putih) bisa menang dalam 1 move
                    if (color == 2 && criticalCell.contains(i)){
                        //jika ya, move mutlak diambil. Jika tidak, kita akan kalah
                        finalMove = move;
                        //teruskan pengecekan, siapa tau ada move yang bisa membuat kita menang
                        continue moveSearch;
                    }
                }
            }
        }
        if (debug){ //debugger
            System.out.println(mover + " final move: " + finalMove);
        }
        // memilih final move berdasarkan child selection policy jika tidak ada move yang mutlak harus diambil
        if (finalMove == null){
            finalMove = finalMoveSelection(root, player);
        }
        
        //pending
        game.apply(context, finalMove);
        sentinel = new Node(root, finalMove, context);
        
        return finalMove;
    }
    
    /**
     * Method untuk melakukan play-out dengan heuristik dan evaluation function
     * @param context Konteks (state) permainan saat ini
     * @param maxIteration batasan kedalaman/iterasi play-out
     */
    public static void playoutHeuristic(Context context, int maxIteration) {
        Game game = context.game(); //mengambil objek Game dari Context
        
        int iteration = 0; //jumlah iterasi atau kedalaman playout
        //Melakukan playout selama belum mencapai terminal state dan jumlah iterasi masih mencukupi
        while(!context.trial().over() && iteration < maxIteration) {
            //mengambil list semua Move yang bisa dilakukan
            final FastArrayList<Move> legalMoves = game.moves(context).moves();
            Random rand = ThreadLocalRandom.current();
            //membuat list untuk menyimpan move dengan nilai heuristiknya
            WeightedMove listMove[] = new WeightedMove[legalMoves.size()];
            //menghitung nilai heuristik untuk semua move dan menyimpannya
            for (int i = 0 ; i < legalMoves.size() ; i++) {
                double heuristicValue = heuristicFunction(context, legalMoves.get(i));
                listMove[i] = new WeightedMove(legalMoves.get(i), heuristicValue);
            }
            //mengurutkan move berdasarkan nilai heuristik dari besar ke kecil
            Arrays.sort(listMove);
            
            Move nextMove;
            //memeriksa apakah player pasti menang (1000000 artinya sudah menang, 999999 artinya berhasil mencegah musuh untuk menang)
            if(listMove[0].heuristicValue == 1000000 || listMove[0].heuristicValue == 999999) {
                nextMove = listMove[0].move;
                break;
            }
            //tidak ada move yang mutlak harus dilakukan
            else if (listMove[0].heuristicValue != -1000000){
                //memilih move random dengan teknik roullete
                //move dengan nilai heuristik lebih tinggi memiliki peluaang lebih tinggi daripada move dengan heuristik rendah.

                FastArrayList<WeightedMove> roulette = new FastArrayList<>();
                double minValue = listMove[0].heuristicValue; //nilai heuristik terkecil
                double totalValue = 0; //total nilai heuristik
                for (int i = 0; i < listMove.length; i++) {
                    //hanya mengambil move yang tidak akan menyebabkan kekalahan
                    if(listMove[i].heuristicValue != -1000000) {
                        roulette.add(listMove[i]);
                        minValue = Math.min(minValue, listMove[i].heuristicValue);
                        totalValue += listMove[i].heuristicValue;
                    } else {
                        break;
                    }
                }
                
                //normalimasi nilai heuristik jika ada yang negatif
                if(minValue < 0) {
                    for (WeightedMove wm : roulette) {
                        wm.heuristicValue += Math.abs(minValue);
                    }
                    totalValue += roulette.size() * Math.abs(minValue);
                }
                
                //memilih nilai random untuk roulette
                double selectedValue = ThreadLocalRandom.current().nextDouble(0, totalValue);
                double lowerBound = 0;
                double upperBound = 0;
                nextMove = roulette.get(0).move; // pasti dioverwrite
                //mencari nilai move yang terpilih oleh roulette
                for (int i = 0; i < roulette.size(); i++) {
                    upperBound += roulette.get(i).heuristicValue;
                    if(selectedValue >= lowerBound && selectedValue <= upperBound) {
                        nextMove = roulette.get(i).move;
                        break;
                    }
                    lowerBound = upperBound;
                }
            }else { 
                //pemain saat ini sudah pasti akan kalah
                //memilih move random karena sudah pasti akan kalah
                nextMove = legalMoves.get(rand.nextInt(legalMoves.size()));
            }
            
            game.apply(context, nextMove); //mengapply move yang sudah dipilih
        }
    }
    
    /**
     * fungsi heuristik untuk menilai state yang akan dituju dari move tertentu
     * @param context Konteks (state) permainan saat ini
     * @param game Objek permainan Knightthrough
     * @param move Move yang akan menghasilkan state yang akan dinilai
     * @return nilai heuristik dari state
     */
    public static double heuristicFunction(Context context, Move move) {
        Game game = context.game(); //mengambil objek Game dari Context
        final int mover = context.state().mover(); // player yang mendapat giliran saat ini
        // state papan sebelum diapply move
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell();
        // set untuk menyimpan cell-cell yang "kritis"
        Set<Integer> criticalCell = new HashSet<>();
        
        //memeriksa setiap cell
        for (int i = 0; i < 64; i++) {
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(mover == 1 ) { //saat ini giliran player 1 (putih)
                if(color == 2 && (y == 1 || y == 2)) {
                    //ada kuda musuh (hitam) yang bisa menang dalam 1 langkah
                    criticalCell.add(i);
                }
            }
            else { //saat ini giliran player 2 (hitam)
                if(color == 1 && (y == 5 || y == 6)) {
                    //ada kuda musuh (putih) yang bisa menang dalam 1 langkah
                    criticalCell.add(i);
                }
            }
        }
        
        //mengcopy context supaya context asli tidak berubah
        Context context2 = new Context(context);
        game.apply(context2, move);
        //Menyimpan state dari context yang sudah dipindahkan
        ChunkSet chunks = context2.state().containerStates()[0].cloneWhoCell();
        
        FastArrayList<Integer> pos1 = new FastArrayList<>(); // ArrayList yang menampung posisi-posisi dari kuda player 1
        FastArrayList<Integer> pos2 = new FastArrayList<>(); // ArrayList yang menampung posisi-posisi dari kuda player 2
        
        int state[][] = new int[8][8]; // representasi papan dari state saat ini
        double heuristicValue = 0; //variabel yang menyimpan nilai heuristik dari state
        
        //flag yang menandai apakah player saat ini sudah pasti akan kalah
        boolean fixKalah = false;
        //memeriksa setiap cell
        for (int i = 0; i < 64; i++) {
            int color = chunks.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            
            if(color == 1) { // cell ini berisi kuda putih
                pos1.add(i); // menambah cell ke ArrayList
                if(y == 7 && mover == 1){ // kuda player putih sudah sampai ke goal
                    return 1000000; // heuristic value diset Infinity agar move ini pasti terpilih
                }
                else if(mover == 1 && (y == 1 || y == 2)) {
                    if(criticalCell.contains(i)) {
                        //player 1 berhasil memakan kuda lawan yang akan menang dalam 1 langkah lagi
                        return 999999;
                    }
                }
                else if(y >= 5 && mover == 2) { //player tidak bisa memakan musuh yang akan sampai ke goal
                    fixKalah = true;
                }
            }
            else { // cell ini berisi kuda hitam
                pos2.add(i);
                if(y == 0){ // kuda player hitam sudah sampai ke goal
                    return 1000000; // heuristic value diset Infinity agar move ini pasti terpilih
                }
                else if(mover == 2 && (y == 5 || y == 6)) {
                    if(criticalCell.contains(i)) {
                        //player 2 berhasil memakan kuda lawan yang akan menang dalam 1 langkah lagi
                        return 999999;
                    }
                }
                else if(y <= 2 && mover == 1) { //player tidak bisa memakan musuh yang akan sampai ke goal
                    fixKalah = true;
                }
            }
            
            state[y][x] = color; // update papan
        }
        
        if (fixKalah) return -1000000;
        
        if(mover == 1) { // giliran saat ini adalah player 1
            //untuk setiap kuda berwarna putih
            for (int i : pos1) {
                int x = i%8, y = i/8; //ambil posisi kolom dan barisnya
                
                // menambah heuristik dengan jumlah kuda sekutu yang menjaga kuda saat ini
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
                
                // mengurangi heuristik dengan jumlah kuda lawan yang mengancam kuda saat ini
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
        }
        else { // giliran saat ini adalah player 2
            //untuk setiap kuda berwarna hitam
            for (int i : pos2) {
                int x = i%8, y = i/8; //ambil posisi kolom dan barisnya
                
                // menambah heuristik dengan jumlah kuda sekutu yang menjaga kuda saat ini
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
                
                // mengurangi heuristik dengan jumlah kuda lawan yang mengancam kuda saat ini
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
     * method untuk mengevaluasi utility dari sebuah state.
     * Nilai yang dipertimbangkan adalah jumlah kuda dan formasi kuda
     * @param context Konteks (state) permainan saat ini
     * @return array double berisi nilai utility untuk setiap player
     */
    public static double[] evaluationFunction(Context context) {
        //mengambil board state dari context
        ChunkSet chunks = context.state().containerStates()[0].cloneWhoCell();
        int white = 0; // jumlah kuda player 1
        int black = 0; // jumlah kuda player 2
        for (int i = 0; i < 64; i++) { // loop semua cell dalam board
            if(chunks.getChunk(i) == 1) {
                white++; // jika kuda berwarna putih, jumlah kuda player 1 ditambah
            }else {
                black++; // jika kuda berwarna hitam, jumlah kuda player 2 ditambah
            }
        }
        
        // Critical cell adalah cell dimana jika ada kuda musuh menempati cell tersebut, maka 1 langkah lagi musuh pasti menang.
        int guardedCriticalWhite = 0; // jumlah critical cell player 1 yang dijaga. 
        int guardedCriticalBlack = 0; // jumlah critical cell player 2 yang dijaga. 
        
        // hitung guarded critical cell player 1
        for (int i = 1; i <= 2; i++) { // critical cell berada pada baris 1 dan 2
            for (int j = 0; j < 8; j++) { // critical cell berada pada kolom 0 sampai 7
                
                if(j >= 1 && i >= 2) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i-2)*8 + (j-1); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 1) { // critical cell terjaga
                        guardedCriticalWhite ++; // counter critical cell player 1 yang terjaga ditambahkan
                        continue; 
                    }
                }
                
                if(j <= 6 && i >= 2) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i-2)*8 + (j+1); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 1) { // critical cell terjaga
                        guardedCriticalWhite ++; // counter critical cell player 1 yang terjaga ditambahkan
                        continue;
                    }
                }
                
                if(j >= 2) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i-1)*8 + (j-2); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 1) { // critical cell terjaga
                        guardedCriticalWhite ++; // counter critical cell player 1 yang terjaga ditambahkan
                        continue;
                    }
                }
                
                if(j <= 5) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i-1)*8 + (j+2); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 1) { // critical cell terjaga
                        guardedCriticalWhite ++; // counter critical cell player 1 yang terjaga ditambahkan
                        continue;
                    }
                }
            }
        }
        
        // hitung guarded critical cell player 2
        for (int i = 5; i <= 6; i++) { // critical cell berada pada baris 5 dan 6
            for (int j = 0; j < 8; j++) { // critical cell berada pada kolom 0 sampai 7
                
                if(j >= 1 && i <= 5) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i+2)*8 + (j-1); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 2) { // critical cell terjaga
                        guardedCriticalBlack ++; // counter critical cell player 2 yang terjaga ditambahkan
                        continue; 
                    }
                }
                
                if(j <= 6 && i <= 5) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i+2)*8 + (j+1); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 2) { // critical cell terjaga
                        guardedCriticalBlack ++; // counter critical cell player 2 yang terjaga ditambahkan
                        continue;
                    }
                }
                
                if(j >= 2) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i+1)*8 + (j-2); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 2) { // critical cell terjaga
                        guardedCriticalBlack ++; // counter critical cell player 2 yang terjaga ditambahkan
                        continue;
                    }
                }
                
                if(j <= 5) { // cek apakah cell yang mau dicek berada dalam papan
                    int nomorChunk = (i+1)*8 + (j+2); // nomor chunk pada papan
                    if(chunks.getChunk(nomorChunk) == 2) { // critical cell terjaga
                        guardedCriticalBlack ++; // counter critical cell player 2 yang terjaga ditambahkan
                        continue;
                    }
                }
            }
        }
        
        // perhitungan evaluation value
        double eval1 = white + guardedCriticalWhite; // evaluation value player 1
        double eval2 = black + guardedCriticalBlack; // evaluation value player 2
        
        //menambah bobot eval yang bernilai lebih tinggi supaya pengaruhnya lebih besar
        if(eval1 > eval2) eval1*=2;
        else eval2*=2;
        
        double totalEval = eval1 + eval2;
        
        double evaluation[] = new double[3]; // array hasil evaluation function
        evaluation[1] = eval1/totalEval; //normalisasi nilai eval
        evaluation[2] = eval2/totalEval; //normalisasi nilai eval
        
        return evaluation;
    }

    /**
     * Melakukan seleksi dengan rumus UCB1 sekaligus melakukan ekspansi pohon
     * @param current Node saat ini
     * @return Node yang terpilih. Bila nilai visitnya == 0, artinya node tersebut baru saja diinstansiasi
     */
    public static Node select(final Node current) {
        //jika node saat ini belum sepenuhnya diekspansi
        if (!current.unexpandedMoves.isEmpty()) {
            //memilih salah satu Move secara acak
            final Move move = current.unexpandedMoves.remove(
                    ThreadLocalRandom.current().nextInt(current.unexpandedMoves.size()));

            // membuat copy dari context saat ini
            final Context context = new Context(current.context);

            // meng-apply move supaya kita mendapatkan konteks tetangganya
            context.game().apply(context, move);

            // menginstansiasi node baru dan menghubungkannya ke game tree
            Node newNode = new Node(current, move, context);
            // memasukan node baru ke dalam hashmap
            visited.put(newNode.nodeHash(), newNode);
            //mengembalikan Node yang baru diinstansiasi
            return newNode;
        }

        // menggunakan UCB1 untuk memilih node child. Bila ada yang seri, pilih anak secara random
        Node bestChild = null; //node anak yang terbaik
        double bestValue = Double.NEGATIVE_INFINITY; //nilau UCB1 yang tertinggi
        //menghitung porsi UCB1 yang kosntan
        final double twoParentLog = Math.pow(eksplorasi, 2) * Math.log(Math.max(1, current.visitCount));
        int numBestFound = 0; //jumlah node terbaik yang ditemukan

        final int numChildren = current.children.size(); //jumlah node anak dari node saat ini
        final int mover = current.context.state().mover(); //pemain yang sedang mendapat giliran

        for (int i = 0; i < numChildren; ++i) { //untuk setiap node anak
            final Node child = current.children.get(i);
            final double exploit = child.scoreSums[mover] / child.visitCount; //hitung nilai eksploitasi
            final double explore = Math.sqrt(twoParentLog / child.visitCount); //hitung nilai eksploitasi

            final double ucb1Value = exploit + explore; //menghitung nilai UCB1

            if (ucb1Value > bestValue) { //kita menemukan node dengan nilai tertinggi
                //menyimpan nilai, dan node anaknya
                bestValue = ucb1Value;
                bestChild = child;
                numBestFound = 1;
            } else if (ucb1Value == bestValue
                    && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                // ada node yang memiliki nilai UCB1 yang sama, mengambil salah satu node secara acak
                bestChild = child;
            }
        }
        
        return bestChild;
    }

    /**
     * Memilih Move yang akan dijalankan sesuai dengan Child Selection Policy
     * Child Selection Policy yang digunakan adalah "Max Child", yaitu memilih
     * child node dengan nilai tertinggi
     * @param rootNode root node untuk state game saat ini
     * @param playerId id dari player yang sedang mendapat giliran
     * @return
     */
    public static Move finalMoveSelection(final Node rootNode, int playerId) {
        Node bestChild = null; //variabel untuk menyimpan Node terbaik
        double bestValue = Integer.MIN_VALUE; //variabel untuk menyimpan nilai Node tertinggi
        int numBestFound = 0; //variabel untuk menyimpan jumlah Node yang sama-sama memiliki 
        
        final int numChildren = rootNode.children.size(); //jumlah node tetangga/anak dari node root
        
        for (int i = 0; i < numChildren; ++i) { //untuk setiap node anak
            final Node child = rootNode.children.get(i);
            final double value = child.scoreSums[playerId]; //ambil nilai dari node anak

            if (value > bestValue) { //kita menemukan node dengan nilai tertinggi
                //menyimpan nilai dan node yang terbaik
                bestValue = value;
                bestChild = child;
                numBestFound = 1;
            } else if (value == bestValue
                    && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                // ada node yang memiliki nilai yang sama, mengambil salah satu node secara acak
                bestChild = child;
            }
        }

        return bestChild.moveFromParent; //menyembalikan move yang menyebabkan perpindahan ke state tersebut
    }

    /**
     * method library untuk menginisialisasi agen. Pada agen ini, nilai yang diinisialisasi hanya id pemainnya
     * @param game Objek game
     * @param playerID id pemain untuk agen ini
     */
    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;
    }

    /**
     * Method yang memeriksa apakah agen dapat memainkan game yagn diberikan
     * @param game Objek Game
     * @return true bila agen bisa memanikan game, false bila tidak
     */
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
     * Inner class untuk node yang akan digunakan dalam algoritma MCTS
     * @author Dennis Soemers https://github.com/Ludeme/LudiiExampleAI/blob/master/src/mcts/ExampleUCT.java
     * Diadaptasi oleh Jiang Han dan Warren Mazmur
     */
    private static class Node {

        /**
         * Node parent dari node ini
         */
        private Node parent;

        /**
         * Konteks (state) yang direpresentasikan oleh node ini
         */
        private final Context context;

        /**
         * Jumlah frekuensi kunjungan ke node ini
         */
        private int visitCount = 0;

        /**
         * Nilai utility pagi setiap pemain pada node ini
         */
        private final double[] scoreSums;

        /**
         * List node anak dari node ini
         */
        private final List<Node> children = new ArrayList<Node>();
        
        /**
         * Objek Mode yang menyebabkan perpindahan state dari parent state ke state ini
         */
        private final Move moveFromParent;

        /**
         * List dari move-move menuju node yang belum pernah di-expand
         */
        private final FastArrayList<Move> unexpandedMoves;

        /**
         * Konstruktor
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
            
            //Menyimpan semua move yang valid
            unexpandedMoves = new FastArrayList<>(game.moves(context).moves());

            if (parent != null) {
                //jika node ini memiliki parent, tambahkan node ini ke list node anak milik parent 
                parent.children.add(this);
            }
        }
        
        /**
         * Method untuk mengembalikan hasil hash dari state node dengan node parentnya
         * @return String yang merupakan hasil hashing
         */
        public String nodeHash() {
            if (parent==null){
                //jika node ini tidak punya parent, kembalikan string whoCell-nya yang diconcantenate dengan "null"
                return (context.state().containerStates()[0].cloneWhoCell().toString() + "null");
            } else {
                //jika node ini punya parent, kembalikan hasil concatenation antara whoCell-nya dengan who-cell parentnya
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
    
    /**
     * method utility untuk membantu mencetak gambar papan permainan ke layar
     * @param context Konteks (state)) game yang ingin dicetak
     */
    private static void printBoard(Context context){
        ChunkSet chunksInitial = context.state().containerStates()[0].cloneWhoCell(); // state papan
        int states[][] = new int[8][8]; //array 2 dimensi untuk menyimpan gambar papan
        for (int i = 0; i < 64; i++) { //untuk setiap cell pada papan
            int color = chunksInitial.getChunk(i); // nomor player yang menempati posisi i
            int x = i%8, y = i/8; // x : posisi kotak secara horizontal, y : posisi kotak secara vertikal, (0,0) berada di kiri bawah papan
            states[y][x] = color; // menyimpan id pemain pada array 2D
        }
        //Mencetak papan ke layar
        for (int i = 7; i>=0; i--){
            for (int j = 0; j < 8; j++) {
                System.out.print(states[i][j] + " ");
            }
            System.out.println("");
        }
    }
}
