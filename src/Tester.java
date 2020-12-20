
import java.util.ArrayList;
import java.util.List;

import game.Game;
import util.AI;
import util.Context;
import util.GameLoader;
import util.Move;
import util.Trial;

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
        
        // Siapkan 10 agen dengan parameter eksplorasi berbeda-beda
        ArrayList<Double> param = new ArrayList<>();
        for (double p = 1; p <= 3; p += 0.2) {
            param.add(p);
        }
       
        
        int winCount[] = new int[param.size()]; // array yang menyimpan total kemenangan setiap agen
        boolean versus[][] = new boolean[param.size()][param.size()]; //baris i kolom j true jika agen i mengalahkan agen j
        
        // round robin tournament
        for (int i = 0; i < param.size() - 1; i++) {
            for (int j = i + 1; j < param.size(); j++) {
                System.gc(); // panggil garbage collector untuk menghindari memory leak
                
                // instansiasi object Trial dan Context
                Trial trial = new Trial(game);
                Context context = new Context(game, trial);
                
                
                // instansiasi agen
                final List<AI> agents = new ArrayList<AI>();
                agents.add(null);	// dummy variable, karena nomor player dimulai dari 1
                agents.add(new CustomizedMCTS(Math.sqrt(param.get(i)))); // player 1 (putih)
                agents.add(new CustomizedMCTS(Math.sqrt(param.get(j)))); // player 2 (hitam)

                // banyak game yang ingin dimainkan
                final int numGames = 1;

                for (int g = 0; g < numGames; ++g) { // mainkan sejumlah numGames
                    
                    game.start(context); // start game

                    // inisialisasi agen
                    for (int p = 1; p < agents.size(); ++p) {
                        agents.get(p).initAI(game, p);
                    }

                    // mainkan terus selama game belum berakhir
                    while (!context.trial().over()) {
                        
                        final int mover = context.state().mover(); // nomor player yang mendapat giliran saat ini

                        final AI agent = agents.get(mover); // ambil agen dengan nomor player mover pada list agen

                        // penentuan move oleh agen
                        final Move move = agent.selectAction(
                                game,
                                new Context(context),
                                0.5,
                                -1,
                                -1
                        );

                        // apply move terhadap game
                        game.apply(context, move);

                    }

                    String stat = context.trial().status().toString(); // String status yang berisi pemenang
                    if (stat.equals("Player 1 wins.")) { // player ke-i menang
                        winCount[i]++; // total kemenangan player ke-i bertambah
                        versus[i][j] = true; // player ke-i mengalahkan player ke-j
                        versus[j][i] = false; // player ke-j dikalahkan oleh player ke-i
                        System.out.println(i + " wins against " + j);
                    } else {  // player ke-j menang
                        winCount[j]++; // total kemenangan player ke-j bertambah
                        versus[i][j] = false; // player ke-i dikalahkan oleh player ke-j
                        versus[j][i] = true; // player ke-j mengalahkan player ke-i
                        System.out.println(j + " wins against " + i);
                    }
                    
                }
            }
        }
        
        // print total kemenangan oleh setiap agen
        System.out.println("param wincount");
        for (int i = 0; i < winCount.length; i++) {
            System.out.println(param.get(i) + ": " + winCount[i]);
        } System.out.println("");
        
        // print hasil pertandingan oleh setiap agen dengan setiap agen
        System.out.println("Match result");
        for (int i = 0; i < versus.length; i++) {
            for (int j = 0; j < versus.length; j++) {
                System.out.print((versus[i][j]?1:0) + " ");
            }
            System.out.println("");
        }
    }
}
