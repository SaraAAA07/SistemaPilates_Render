import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class ServidorPilates {
    static final String ARQUIVO = "alunos.csv";

    public static void main(String[] args) throws Exception {
        criarArquivo();

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

    static void criarArquivo() throws IOException {
        Path path = Paths.get(ARQUIVO);
        if (!Files.exists(path)) {
            Files.write(path, List.of("1;Maria Silva;Dor lombar;true", "2;Carlos Souza;Hérnia cervical;false"));
        }
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
            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            int id = alunos.size() + 1;
            String linha = id + ";" + dados.get("nome") + ";" + dados.get("patologia") + ";" + dados.get("emDia");
            alunos.add(linha);
            Files.write(Paths.get(ARQUIVO), alunos);

            redirect(t, "/listar");
        }
    }

    static class ExcluirHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            String id = query.split("=")[1];

            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));
            List<String> novos = new ArrayList<>();

            for (String aluno : alunos) {
                if (!aluno.startsWith(id + ";")) {
                    novos.add(aluno);
                }
            }

            Files.write(Paths.get(ARQUIVO), novos);
            redirect(t, "/listar");
        }
    }

    static class EditarHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (t.getRequestMethod().equals("GET")) {
                String id = t.getRequestURI().getQuery().split("=")[1];
                List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

                for (String aluno : alunos) {
                    String[] p = aluno.split(";");
                    if (p[0].equals(id)) {
                        String html = "<html><head><link rel='stylesheet' href='/style.css'><meta charset='UTF-8'></head><body><main class='main-content'><div class='content-card'><h1>Editar Aluno</h1><form method='POST' action='/editar'><input type='hidden' name='id' value='" + p[0] + "'><input type='text' name='nome' value='" + p[1] + "'><input type='text' name='patologia' value='" + p[2] + "'><select name='emDia'><option value='true'>Em dia</option><option value='false'>Pendente</option></select><button class='btn-primary' type='submit'>Salvar Alterações</button></form></div></main></body></html>";
                        responder(t, html);
                        return;
                    }
                }
            } else {
                Map<String, String> dados = formToMap(t);
                List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));
                List<String> novos = new ArrayList<>();

                for (String aluno : alunos) {
                    String[] p = aluno.split(";");
                    if (p[0].equals(dados.get("id"))) {
                        novos.add(p[0] + ";" + dados.get("nome") + ";" + dados.get("patologia") + ";" + dados.get("emDia"));
                    } else {
                        novos.add(aluno);
                    }
                }

                Files.write(Paths.get(ARQUIVO), novos);
                redirect(t, "/listar");
            }
        }
    }

    static class ListarHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            StringBuilder linhas = new StringBuilder();
            for (String aluno : alunos) {
                String[] p = aluno.split(";");
                String status = p[3].equals("true") ? "<span class='badge badge-success'>Em dia</span>" : "<span class='badge badge-danger'>Pendente</span>";

                linhas.append("<tr>")
                        .append("<td>").append(p[1]).append("</td>")
                        .append("<td>").append(p[2]).append("</td>")
                        .append("<td>").append(status).append("</td>")
                        .append("<td><a class='action-btn edit-btn' href='/editar?id=").append(p[0]).append("'>Editar</a> ")
                        .append("<a class='action-btn delete-btn' href='/excluir?id=").append(p[0]).append("'>Excluir</a></td>")
                        .append("</tr>");
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
