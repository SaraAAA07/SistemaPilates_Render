import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class ServidorPilates {

    // CONFIGURAÇÕES DO SISTEMA
    static final String SENHA_BD = "ems@uri.santiago2026"; 
    static final String NOME_LOGO = "logo.png"; 

    public static void main(String[] args) throws Exception {
        // Criando o servidor na porta 8080
        HttpServer servidor = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // --- ROTAS DE ARQUIVOS EXTERNOS ---
        servidor.createContext("/style.css", new MostrarCssHandler());
        servidor.createContext("/" + NOME_LOGO, new MostrarImagemHandler());

        // --- ROTAS DE NAVEGAÇÃO (HTML) ---
        servidor.createContext("/", new MostrarHtmlHandler("login.html"));
        servidor.createContext("/admin", new MostrarHtmlHandler("admin.html"));
        servidor.createContext("/aluno", new MostrarHtmlHandler("aluno.html"));
        servidor.createContext("/cadastro", new MostrarHtmlHandler("cadastro.html"));
        
        // --- ROTAS DE LÓGICA (BANCO E LOGIN) ---
        servidor.createContext("/autenticar", new LoginHandler());
        servidor.createContext("/salvar", new SalvarBancoHandler());
        servidor.createContext("/listar", new ListarAlunosHandler());
        
        servidor.setExecutor(null); 
        servidor.start();
        
        System.out.println("Sistema Pilates Gindri ONLINE!");
        System.out.println("Acesse no Chrome: http://localhost:8080");
    }

    // 1. HANDLER PARA O CSS (Essencial para o visual novo)
    static class MostrarCssHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                byte[] css = Files.readAllBytes(Paths.get("style.css"));
                t.getResponseHeaders().set("Content-Type", "text/css");
                t.sendResponseHeaders(200, css.length);
                OutputStream os = t.getResponseBody();
                os.write(css);
                os.close();
            } catch (IOException e) {
                t.sendResponseHeaders(404, 0);
                t.close();
            }
        }
    }

    // 2. HANDLER PARA A LOGO
    static class MostrarImagemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                byte[] imagem = Files.readAllBytes(Paths.get(NOME_LOGO));
                t.getResponseHeaders().set("Content-Type", "image/png"); 
                t.sendResponseHeaders(200, imagem.length);
                OutputStream os = t.getResponseBody();
                os.write(imagem);
                os.close();
            } catch (IOException e) {
                t.sendResponseHeaders(404, 0);
                t.close();
            }
        }
    }

    // 3. HANDLER DE LOGIN (Alice vs Aluno)
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String dados = new String(t.getRequestBody().readAllBytes());
            String[] partes = dados.split("&");
            String usuario = partes[0].split("=")[1];
            String senha = partes[1].split("=")[1];

            if (usuario.equals("alice") && senha.equals("123")) {
                t.getResponseHeaders().add("Location", "/admin");
                t.sendResponseHeaders(302, -1);
            } else if (usuario.equals("aluno") && senha.equals("123")) {
                t.getResponseHeaders().add("Location", "/aluno");
                t.sendResponseHeaders(302, -1);
            } else {
                String erro = "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                              "<h2 style='color:red;'> Usuário ou Senha incorretos!</h2>" +
                              "<a href='/'>Tentar de novo</a></body></html>";
                t.sendResponseHeaders(200, erro.getBytes("UTF-8").length);
                t.getResponseBody().write(erro.getBytes("UTF-8"));
            }
            t.close();
        }
    }

    // 4. HANDLER GERAL PARA TELAS HTML
    static class MostrarHtmlHandler implements HttpHandler {
        private String arquivo;
        public MostrarHtmlHandler(String arquivo) { this.arquivo = arquivo; }
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                byte[] html = Files.readAllBytes(Paths.get(arquivo));
                t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                t.sendResponseHeaders(200, html.length);
                t.getResponseBody().write(html);
                t.getResponseBody().close();
            } catch (IOException e) {
                t.sendResponseHeaders(404, 0);
                t.close();
            }
        }
    }

    // 5. HANDLER PARA SALVAR NO MYSQL
    static class SalvarBancoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String dados = new String(t.getRequestBody().readAllBytes());
            String[] partes = dados.split("&");
            String nome = partes[0].split("=")[1].replace("+", " ");
            String patologia = partes[1].split("=")[1].replace("+", " ");
            boolean emDia = partes[2].split("=")[1].equals("true");
            
            String msg;
            try {
                String url = "jdbc:mysql://localhost:3306/gindri_pilates";
                Connection conexao = DriverManager.getConnection(url, "root", SENHA_BD);
                String sql = "INSERT INTO alunos (nome, patologia, pagamento_em_dia) VALUES (?, ?, ?)";
                PreparedStatement comando = conexao.prepareStatement(sql);
                comando.setString(1, java.net.URLDecoder.decode(nome, "UTF-8"));
                comando.setString(2, java.net.URLDecoder.decode(patologia, "UTF-8"));
                comando.setBoolean(3, emDia);
                comando.execute();
                conexao.close();
                msg = "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                      "<h2 style='color:green;'>✅ Aluno Salvo com Sucesso!</h2>" +
                      "<a href='/admin'>Voltar ao Painel</a></body></html>";
            } catch (Exception e) {
                msg = "<h2>Erro no Banco: " + e.getMessage() + "</h2>";
            }
            t.sendResponseHeaders(200, msg.getBytes("UTF-8").length);
            t.getResponseBody().write(msg.getBytes("UTF-8"));
            t.getResponseBody().close();
        }
    }

    // 6. HANDLER PARA LISTAR ALUNOS COM O VISUAL NOVO
    static class ListarAlunosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'><link rel='stylesheet' href='style.css'></head><body>");
            
            // Menu Lateral na listagem também para manter o padrão
            html.append("<nav class='sidebar'><div class='logo-container'><img src='/logo.png'></div>")
                .append("<a href='/admin' class='nav-link'> Painel</a>")
                .append("<a href='/listar' class='nav-link active'> Alunos</a>")
                .append("<a href='/cadastro' class='nav-link'> Novo Cadastro</a>")
                .append("<a href='/' class='nav-link' style='margin-top:auto; color:red;'>🚪 Sair</a></nav>");

            html.append("<main class='main-content'><h1> Listagem de Alunos</h1><div class='content-card'><table>")
                .append("<tr><th>Nome do Aluno</th><th>Patologia</th><th>Status</th></tr>");

            try {
                String url = "jdbc:mysql://localhost:3306/gindri_pilates";
                Connection conexao = DriverManager.getConnection(url, "root", SENHA_BD);
                Statement stmt = conexao.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM alunos");

                while(rs.next()) {
                    String statusClass = rs.getBoolean("pagamento_em_dia") ? "badge-success" : "badge-danger";
                    String statusText = rs.getBoolean("pagamento_em_dia") ? "Em Dia" : "Pendente";
                    
                    html.append("<tr><td>").append(rs.getString("nome")).append("</td>")
                        .append("<td>").append(rs.getString("patologia")).append("</td>")
                        .append("<td><span class='badge ").append(statusClass).append("'>")
                        .append(statusText).append("</span></td></tr>");
                }
                conexao.close();
            } catch (Exception e) {
                html.append("<tr><td colspan='3'>Erro: ").append(e.getMessage()).append("</td></tr>");
            }

            html.append("</table></div></main></body></html>");

            byte[] resposta = html.toString().getBytes("UTF-8");
            t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            t.sendResponseHeaders(200, resposta.length);
            t.getResponseBody().write(resposta);
            t.getResponseBody().close();
        }
    }
}