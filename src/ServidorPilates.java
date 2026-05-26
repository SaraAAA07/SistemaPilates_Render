import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class ServidorPilates {

    static final String ARQUIVO = "alunos.csv";

    static String alunoLogado = "";
    static String[] dadosAlunoLogado = null;

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

        servidor.createContext("/dadosAluno", exchange -> {

            String json =
                    "{"
                            + "\"nome\":\"" + (dadosAlunoLogado != null ? dadosAlunoLogado[1] : "") + "\","
                            + "\"patologia\":\"" + (dadosAlunoLogado != null ? dadosAlunoLogado[2] : "") + "\","
                            + "\"frequencia\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 8 ? dadosAlunoLogado[7] : "") + "\","
                            + "\"pontos\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 9 ? dadosAlunoLogado[8] : "0") + "\","
                            + "\"pagamento\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 10 ? dadosAlunoLogado[9] : "") + "\","
                            + "\"aulas\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 11 ? dadosAlunoLogado[10] : "") + "\","
                            + "\"horarios\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 12 ? dadosAlunoLogado[11] : "") + "\","
                            + "\"modalidades\":\"" + (dadosAlunoLogado != null && dadosAlunoLogado.length >= 13 ? dadosAlunoLogado[12] : "") + "\""
                            + "}";

            byte[] resp = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");

            exchange.sendResponseHeaders(200, resp.length);

            exchange.getResponseBody().write(resp);

            exchange.close();
        });

        servidor.createContext("/estatisticas", exchange -> {

            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            int total = alunos.size();

            int inadimplentes = 0;

            for (String aluno : alunos) {

                String[] p = aluno.split(";");

                if (p.length >= 10) {

                    if (
                            p[9].equalsIgnoreCase("Pendente")
                                    || p[9].equalsIgnoreCase("Atrasado")
                    ) {

                        inadimplentes++;
                    }
                }
            }

            String json =
                    "{"
                            + "\"total\":" + total + ","
                            + "\"inadimplentes\":" + inadimplentes
                            + "}";

            byte[] resp = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");

            exchange.sendResponseHeaders(200, resp.length);

            exchange.getResponseBody().write(resp);

            exchange.close();
        });

        servidor.start();

        System.out.println("Servidor online na porta " + porta);
    }

    static void criarArquivo() throws IOException {

        Path path = Paths.get(ARQUIVO);

        if (!Files.exists(path)) {

            Files.write(
                    path,
                    List.of(
                            "1;Maria Silva;Dor lombar;true;sim;maria;123;2x Semana;120;Pago;10;Segunda 08:00|Quarta 08:00;Pilates Solo|Funcional"
                    )
            );
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
                return;
            }

            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            for (String aluno : alunos) {

                String[] p = aluno.split(";");

                if (p.length >= 7) {

                    String usuarioAluno = p[5];
                    String senhaAluno = p[6];

                    if (usuario.equals(usuarioAluno) && senha.equals(senhaAluno)) {

                        alunoLogado = p[1];
                        dadosAlunoLogado = p;

                        redirect(t, "/aluno");

                        return;
                    }
                }
            }

            responder(
                    t,
                    "<h2 style='font-family:sans-serif;text-align:center;margin-top:50px;'>Login inválido.<br><a href='/'>Voltar</a></h2>"
            );
        }
    }

    static class SalvarHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            Map<String, String> dados = formToMap(t);

            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            int id = alunos.size() + 1;

            String linha =
                    id + ";" +
                            dados.get("nome") + ";" +
                            dados.get("patologia") + ";" +
                            dados.get("emDia") + ";" +
                            dados.getOrDefault("acesso", "nao") + ";" +
                            dados.getOrDefault("usuario", "") + ";" +
                            dados.getOrDefault("senha", "") + ";" +
                            dados.getOrDefault("frequencia", "") + ";" +
                            dados.getOrDefault("pontos", "0") + ";" +
                            dados.getOrDefault("pagamento", "Pendente") + ";" +
                            dados.getOrDefault("aulasRealizadas", "0") + ";" +
                            dados.getOrDefault("datasAulas", "") + ";" +
                            dados.getOrDefault("catalogoAulas", "");

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

                        String html =
                                "<html><head><meta charset='UTF-8'><link rel='stylesheet' href='/style.css'></head><body>" +

                                        "<main class='main-content'>" +

                                        "<div class='content-card'>" +

                                        "<h1>Editar aluno</h1>" +

                                        "<form method='POST' action='/editar'>" +

                                        "<input type='hidden' name='id' value='" + p[0] + "'>" +

                                        "<label>Nome</label>" +
                                        "<input type='text' name='nome' value='" + p[1] + "'>" +

                                        "<label>Patologia</label>" +
                                        "<input type='text' name='patologia' value='" + p[2] + "'>" +

                                        "<label>Status pagamento</label>" +
                                        "<select name='emDia'>" +
                                        "<option value='true'>Em dia</option>" +
                                        "<option value='false'>Pendente</option>" +
                                        "</select>" +

                                        "<label>Acesso ao sistema</label>" +
                                        "<select name='acesso'>" +
                                        "<option value='sim'>Sim</option>" +
                                        "<option value='nao'>Não</option>" +
                                        "</select>" +

                                        "<label>Usuário</label>" +
                                        "<input type='text' name='usuario' value='" + (p.length >= 6 ? p[5] : "") + "'>" +

                                        "<label>Senha</label>" +
                                        "<input type='text' name='senha' value='" + (p.length >= 7 ? p[6] : "") + "'>" +

                                        "<label>Frequência</label>" +
                                        "<input type='text' name='frequencia' value='" + (p.length >= 8 ? p[7] : "") + "'>" +

                                        "<label>Pontos fidelidade</label>" +
                                        "<input type='number' name='pontos' value='" + (p.length >= 9 ? p[8] : "0") + "'>" +

                                        "<label>Pagamento</label>" +
                                        "<input type='text' name='pagamento' value='" + (p.length >= 10 ? p[9] : "") + "'>" +

                                        "<label>Aulas realizadas</label>" +
                                        "<input type='number' name='aulasRealizadas' value='" + (p.length >= 11 ? p[10] : "0") + "'>" +

                                        "<label>Horários</label>" +
                                        "<textarea name='datasAulas'>" + (p.length >= 12 ? p[11] : "") + "</textarea>" +

                                        "<label>Modalidades</label>" +
                                        "<textarea name='catalogoAulas'>" + (p.length >= 13 ? p[12] : "") + "</textarea>" +

                                        "<button class='btn-primary'>Salvar alterações</button>" +

                                        "</form>" +

                                        "</div>" +

                                        "</main>" +

                                        "</body></html>";

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

                        novos.add(
                                p[0] + ";" +
                                        dados.get("nome") + ";" +
                                        dados.get("patologia") + ";" +
                                        dados.get("emDia") + ";" +
                                        dados.getOrDefault("acesso", "nao") + ";" +
                                        dados.getOrDefault("usuario", "") + ";" +
                                        dados.getOrDefault("senha", "") + ";" +
                                        dados.getOrDefault("frequencia", "") + ";" +
                                        dados.getOrDefault("pontos", "0") + ";" +
                                        dados.getOrDefault("pagamento", "") + ";" +
                                        dados.getOrDefault("aulasRealizadas", "0") + ";" +
                                        dados.getOrDefault("datasAulas", "") + ";" +
                                        dados.getOrDefault("catalogoAulas", "")
                        );

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

                String status =
                        p[3].equals("true")
                                ? "<span class='badge badge-success'>Em dia</span>"
                                : "<span class='badge badge-danger'>Pendente</span>";

                String acesso =
                        p.length >= 5 && p[4].equals("sim")
                                ? "<span class='badge badge-success'>Possui acesso</span>"
                                : "<span class='badge badge-danger'>Sem acesso</span>";

                linhas.append("<tr>")
                        .append("<td>").append(p[1]).append("</td>")
                        .append("<td>").append(p[2]).append("</td>")
                        .append("<td>").append(status).append("</td>")
                        .append("<td>").append(acesso).append("</td>")
                        .append("<td>").append(p.length >= 8 ? p[7] : "").append("</td>")
                        .append("<td>").append(p.length >= 9 ? p[8] : "0").append("</td>")
                        .append("<td>").append(p.length >= 10 ? p[9] : "").append("</td>")
                        .append("<td>").append(p.length >= 11 ? p[10] : "").append("</td>")
                        .append("<td>").append(p.length >= 12 ? p[11] : "").append("</td>")
                        .append("<td>").append(p.length >= 13 ? p[12] : "").append("</td>")
                        .append("<td>")

                        .append("<a class='action-btn edit-btn' href='/editar?id=")
                        .append(p[0])
                        .append("'>Editar</a> ")

                        .append("<a class='action-btn delete-btn' href='/excluir?id=")
                        .append(p[0])
                        .append("'>Excluir</a>")

                        .append("</td>")
                        .append("</tr>");
            }

            String html =
                    "<html>" +

                            "<head>" +
                            "<meta charset='UTF-8'>" +
                            "<link rel='stylesheet' href='/style.css'>" +
                            "<title>Alunos</title>" +
                            "</head>" +

                            "<body>" +

                            "<nav class='sidebar'>" +

                            "<div class='logo-container'>" +
                            "<img src='/logo.png'>" +
                            "</div>" +

                            "<a href='/admin' class='nav-link'>Painel</a>" +
                            "<a href='/listar' class='nav-link active'>Alunos</a>" +
                            "<a href='/cadastro' class='nav-link'>Novo Cadastro</a>" +
                            "<a href='/' class='nav-link'>Sair</a>" +

                            "</nav>" +

                            "<main class='main-content'>" +

                            "<div class='header'>" +
                            "<p>" + LocalDate.now() + "</p>" +
                            "<h1>Alunos cadastrados</h1>" +
                            "</div>" +

                            "<div class='content-card'>" +

                            "<table border='1'>" +

                            "<tr>" +
                            "<th>Nome</th>" +
                            "<th>Patologia</th>" +
                            "<th>Status</th>" +
                            "<th>Acesso</th>" +
                            "<th>Frequência</th>" +
                            "<th>Pontos</th>" +
                            "<th>Pagamento</th>" +
                            "<th>Aulas</th>" +
                            "<th>Horários</th>" +
                            "<th>Modalidades</th>" +
                            "<th>Ações</th>" +
                            "</tr>" +

                            linhas +

                            "</table>" +

                            "</div>" +

                            "</main>" +

                            "</body>" +

                            "</html>";

            responder(t, html);
        }
    }

    static Map<String, String> formToMap(HttpExchange t) throws IOException {

        String form = new String(
                t.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        Map<String, String> dados = new HashMap<>();

        for (String item : form.split("&")) {

            String[] p = item.split("=");

            if (p.length == 2) {

                dados.put(
                        URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(p[1], StandardCharsets.UTF_8)
                );
            }
        }

        return dados;
    }

    static void responder(HttpExchange t, String html) throws IOException {

        byte[] resp = html.getBytes(StandardCharsets.UTF_8);

        t.getResponseHeaders().set(
                "Content-Type",
                "text/html; charset=UTF-8"
        );

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


