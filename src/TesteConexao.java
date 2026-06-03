import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class TesteConexao {

    public static void main(String[] args) {

        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String database = System.getenv("DB_NAME");
        String usuario = System.getenv("DB_USER");
        String senha = System.getenv("DB_PASS");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?sslMode=REQUIRED";

        try {
            System.out.println("Tentando conectar ao banco Aiven...");

            Connection conexao = DriverManager.getConnection(url, usuario, senha);

            System.out.println("Conectado com sucesso!");

            String sql = "INSERT INTO alunos (nome, patologia, pagamento_em_dia) VALUES (?, ?, ?)";

            PreparedStatement comando = conexao.prepareStatement(sql);

            comando.setString(1, "Aluno Teste Aiven");
            comando.setString(2, "Teste de conexão");
            comando.setBoolean(3, true);

            comando.execute();

            System.out.println("Aluno salvo no MySQL Aiven!");

            conexao.close();

        } catch (Exception e) {
            System.out.println("Erro ao conectar: " + e.getMessage());
        }
    }
}