import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*; // Importa o JDBC para conversar com o MySQL
import java.time.LocalDate;
import java.util.*;

public class ServidorPilates {

    // Método auxiliar para conectar ao seu banco de dados automaticamente
    private static Connection conectar() throws Exception {
        
        String url = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/gindri_pilates";
        String usuario = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
        String senha = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "ems@uri.santiago2026";

        return DriverManager.getConnection(url, usuario, senha);
    }

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        servidor.createContext("/style.css", new ArquivoHandler("style.css", "text/css"));
        servidor.createContext("/logo.png", new ArquivoHandler("logo.png", "image/png"));

        servidor.createContext("/", new ArquivoHandler("login.html", "text/html; charset=UTF-8"));
        servidor.createContext("/admin", new ArquivoHandler("admin.html", "text/html; charset=UTF-8"));
        servidor.createContext("/aluno", new ArquivoHandler("aluno.html", "text/html; charset=UTF-8"));
        servidor.createContext("/cadastro", new ArquivoHandler("cadastro.html", "text/html; charset=UTF-8"));

        servidor.createContext("/autenticar", new LoginHandler());
        servidor.createContext("/salvar", new SalvarHandler());
        servidor.createContext("/listar", new ListarHandler());
        servidor.createContext("/excluir", new ExcluirHandler());
        servidor.createContext("/editar", new EditarHandler());

        servidor.start();
        System.out.println("Servidor online na porta " + porta);
    }

    static class ArquivoHandler implements HttpHandler {
        private final String arquivo;
        private final String tipo;

        public ArquivoHandler(String arquivo, String tipo) {
            this.arquivo = arquivo;
            this.tipo = tipo;
        }

        public void handle(HttpExchange t) throws IOException {
            byte[] dados = Files.readAllBytes(Paths.get(arquivo));
            t.getResponseHeaders().set("Content-Type", tipo);
            t.sendResponseHeaders(200, dados.length);
            t.getResponseBody().write(dados);
            t.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            Map<String, String> dados = formToMap(t);
            String usuario = dados.getOrDefault("usuario", "");
            String senha = dados.getOrDefault("senha", "");

            if (usuario.equals("alice") && senha.equals("123")) {
                redirect(t, "/admin");
            } else if (usuario.equals("aluno") && senha.equals("123")) {
                redirect(t, "/aluno");
            } else {
                responder(t, "<h2 style='font-family:sans-serif;text-align:center;margin-top:50px;'>Login inválido.<br><a href='/'>Voltar</a></h2>");
            }
        }
    }

    static class SalvarHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            Map<String, String> dados = formToMap(t);
            
            String nome = dados.get("nome");
            String patologia = dados.get("patologia");
            boolean emDia = Boolean.parseBoolean(dados.get("emDia"));

            // Salva diretamente no MySQL
            try (Connection conn = conectar()) {
                String sql = "INSERT INTO alunos (nome, patologia, pagamento_em_dia) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, nome);
                    stmt.setString(2, patologia);
                    stmt.setBoolean(3, emDia);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            redirect(t, "/listar");
        }
    }

    static class ExcluirHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            int id = Integer.parseInt(query.split("=")[1]);

            // Exclui diretamente no MySQL
            try (Connection conn = conectar()) {
                String sql = "DELETE FROM alunos WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            redirect(t, "/listar");
        }
    }

    static class EditarHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (t.getRequestMethod().equals("GET")) {
                int id = Integer.parseInt(t.getRequestURI().getQuery().split("=")[1]);
                String html = "";

                // Busca os dados atuais do aluno no MySQL para exibir na tela de edição
                try (Connection conn = conectar()) {
                    String sql = "SELECT * FROM alunos WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, id);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                String nome = rs.getString("nome");
                                String patologia = rs.getString("patologia");
                                boolean emDia = rs.getBoolean("pagamento_em_dia");

                                html = "<html><head><link rel='stylesheet' href='/style.css'><meta charset='UTF-8'></head><body><main class='main-content'><div class='content-card'><h1>Editar Aluno</h1><form method='POST' action='/editar'><input type='hidden' name='id' value='" + id + "'><input type='text' name='nome' value='" + nome + "'><input type='text' name='patologia' value='" + patologia + "'><select name='emDia'><option value='true' " + (emDia ? "selected" : "") + ">Em dia</option><option value='false' " + (!emDia ? "selected" : "") + ">Pendente</option></select><button class='btn-primary' type='submit'>Salvar Alterações</button></form></div></main></body></html>";
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                responder(t, html);
            } else {
                Map<String, String> dados = formToMap(t);
                int id = Integer.parseInt(dados.get("id"));
                String nome = dados.get("nome");
                String patologia = dados.get("patologia");
                boolean emDia = Boolean.parseBoolean(dados.get("emDia"));

                // Atualiza os dados no MySQL
                try (Connection conn = conectar()) {
                    String sql = "UPDATE alunos SET nome = ?, patologia = ?, pagamento_em_dia = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, nome);
                        stmt.setString(2, patologia);
                        stmt.setBoolean(3, emDia);
                        stmt.setInt(4, id);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                redirect(t, "/listar");
            }
        }
    }

    static class ListarHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            StringBuilder linhas = new StringBuilder();

            // Busca todos os alunos direto do MySQL para montar a tabela HTML
            try (Connection conn = conectar()) {
                String sql = "SELECT * FROM alunos";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String nome = rs.getString("nome");
                        String patologia = rs.getString("patologia");
                        boolean emDia = rs.getBoolean("pagamento_em_dia");

                        String status = emDia ? "<span class='badge badge-success'>Em dia</span>" : "<span class='badge badge-danger'>Pendente</span>";

                        linhas.append("<tr>")
                                .append("<td>").append(nome).append("</td>")
                                .append("<td>").append(patologia).append("</td>")
                                .append("<td>").append(status).append("</td>")
                                .append("<td><a class='action-btn edit-btn' href='/editar?id=").append(id).append("'>Editar</a> ")
                                .append("<a class='action-btn delete-btn' href='/excluir?id=").append(id).append("'>Excluir</a></td>")
                                .append("</tr>");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String html = "<html><head><meta charset='UTF-8'><link rel='stylesheet' href='/style.css'><title>Alunos</title></head><body>"
                    + "<nav class='sidebar'><div class='logo-container'><img src='/logo.png'></div><a href='/admin' class='nav-link'>Painel</a><a href='/listar' class='nav-link active'>Alunos</a><a href='/cadastro' class='nav-link'>Novo Cadastro</a><a href='/' class='nav-link'>Sair</a></nav>"
                    + "<main class='main-content'><div class='header'><p>" + LocalDate.now() + "</p><h1>Alunos cadastrados</h1></div><div class='content-card'><table><tr><th>Nome</th><th>Patologia</th><th>Status</th><th>Ações</th></tr>"
                    + linhas + "</table></div></main></body></html>";

            responder(t, html);
        }
    }

    static Map<String, String> formToMap(HttpExchange t) throws IOException {
        String form = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> dados = new HashMap<>();

        for (String item : form.split("&")) {
            String[] p = item.split("=");
            if (p.length == 2) {
                dados.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), URLDecoder.decode(p[1], StandardCharsets.UTF_8));
            }
        }
        return dados;
    }

    static void responder(HttpExchange t, String html) throws IOException {
        byte[] resp = html.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, resp.length);
        t.getResponseBody().write(resp);
        t.close();
    }

    static void redirect(HttpExchange t, String url) throws IOException {
        t.getResponseHeaders().add("Location", url);
        t.sendResponseHeaders(302, -1);
        t.close();
    }
}