// Importa as classes necessárias para trabalhar com banco de dados
import java.sql.Connection;          // Representa a conexão com o banco
import java.sql.DriverManager;       // Responsavel por abrir a conexão
import java.sql.PreparedStatement;   // Usado para executar comandos SQL com segurança

public class TesteConexao {

    public static void main(String[] args) {

  
        String url = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/gindri_pilates";
        String usuario = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
        String senha = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "ems@uri.santiago2026";


        try {
            // Mensagem só para mostrar no console que está tentando conectar
            System.out.println("Tentando conectar ao banco do Estúdio...");
            
            // 2. Fazendo a conexão com o banco
            Connection conexao = DriverManager.getConnection(url, usuario, senha);
            // Aqui ele realmente abre a conexão com o MySQL

            System.out.println("DEU CERTO! Conectado com sucesso!");

            // 3. Criando um comando SQL para inserir um aluno
            String sql = "INSERT INTO alunos (nome, patologia, pagamento_em_dia) VALUES (?, ?, ?)";
            // O ? são espaços que depois vamos preencher ( evita erro e ataque SQL)

            // Prepara o comando para ser executado
            PreparedStatement comando = conexao.prepareStatement(sql);

            // Preenche os valores nos ?
            comando.setString(1, "João Silva Teste"); 
            // 1º ? recebe o nome

            comando.setString(2, "Dor Lombar Severa"); 
            // 2º ? recebe a patologia

            comando.setBoolean(3, true); 
            // 3º ? recebe se o pagamento está em dia (true = sim)

            // Executa o comando no banco
            comando.execute();
            
            System.out.println("Aluno João Silva salvo direto no MySQL!");
            
            // 4. Fecha a conexão com o banco (muito importante!)
            conexao.close();

        } catch (Exception e) {
            // Se der erro, mostra a mensagem no console
            System.out.println("Ocorreu um erro: " + e.getMessage());
        }
    }
}