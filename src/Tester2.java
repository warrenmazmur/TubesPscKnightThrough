
import java.util.ArrayList;
import java.util.List;

import game.Game;
import main.collections.ChunkSet;
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
public class Tester2 {

    public static void main(final String[] args) {

        // Load Knightthrough game
        Game game = GameLoader.loadGameFromName("board/race/reach/Knightthrough.lud");
       
        int winCount[] = new int[2]; // array yang menyimpan total kemenangan setiap agen

        // instansiasi object Trial dan Context
        Trial trial = new Trial(game);
        Context context = new Context(game, trial);

        // instansiasi agen
        final List<AI> agents = new ArrayList<AI>();
        agents.add(null);	// dummy variable, karena nomor player dimulai dari 1
        agents.add(new ExampleUCT()); // player 1 (putih)
        agents.add(new CustomizedMCTS()); // player 2 (hitam)

        // banyak game yang ingin dimainkan
        final int numGames = 10;
        
        
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
            if (stat.equals("Player 1 wins.")) { // player ke-1 menang
                winCount[0]++; // total kemenangan player ke-1 bertambah
                System.out.println(1 + " wins against " + 2);
            } else { // player ke-2 menang
                winCount[1]++; // total kemenangan player ke-2 bertambah
                System.out.println(2 + " wins against " + 1);
            }

        }
        
        System.out.println("Wincount");
        for (int i = 0; i < winCount.length; i++) {
            System.out.println((i+1) + ": " + winCount[i]);
        } System.out.println("");
        
    }
}
