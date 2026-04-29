import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class TesteConexao {
    public static void main(String[] args) {
        // 1. O endereço do banco 
        String url = "jdbc:mysql://localhost:3306/gindri_pilates";
        String usuario = "root"; 
        String senha = "ems@uri.santiago2026";    

        try {
            System.out.println("⏳ Tentando conectar ao banco do Estúdio...");
            
            // 2. Fazendo a conexão
            Connection conexao = DriverManager.getConnection(url, usuario, senha);
            System.out.println("✅ DEU CERTO! Conectado com sucesso!");

            // 3. Cadastrando um aluno direto no banco 
            String sql = "INSERT INTO alunos (nome, patologia, pagamento_em_dia) VALUES (?, ?, ?)";
            PreparedStatement comando = conexao.prepareStatement(sql);
            comando.setString(1, "João Silva Teste");
            comando.setString(2, "Dor Lombar Severa");
            comando.setBoolean(3, true); // true = Em Dia
            comando.execute();
            
            System.out.println("Aluno João Silva salvo direto no MySQL!");
            
            // 4. Fechando a porta
            conexao.close();

        } catch (Exception e) {
            System.out.println("Ocorreu um erro: " + e.getMessage());
        }
    }
}