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

    public static void main(String[] args) throws Exception {

        criarArquivo();

        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        servidor.createContext("/style.css", new ArquivoHandler("style.css", "text/css"));
        servidor.createContext("/logo.png", new ArquivoHandler("logo.png", "image/png"));
        servidor.createContext("/studio.jpeg", new ArquivoHandler("studio.jpeg", "image/jpeg"));

        servidor.createContext("/", new ArquivoHandler("login.html", "text/html; charset=UTF-8"));
        servidor.createContext("/admin", new ArquivoHandler("admin.html", "text/html; charset=UTF-8"));
        servidor.createContext("/aluno", new ArquivoHandler("aluno.html", "text/html; charset=UTF-8"));
        servidor.createContext("/cadastro", new ArquivoHandler("cadastro.html", "text/html; charset=UTF-8"));

        servidor.createContext("/autenticar", new LoginHandler());
        servidor.createContext("/salvar", new SalvarHandler());
        servidor.createContext("/listar", new ListarHandler());
        servidor.createContext("/excluir", new ExcluirHandler());
        servidor.createContext("/editar", new EditarHandler());

        servidor.createContext("/nomeAluno", exchange -> {
            byte[] resp = alunoLogado.getBytes(StandardCharsets.UTF_8);
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
                    "1;Maria Silva;Dor lombar;true;sim;maria;123;2x Semana;120;Pago;10;2026-06-02 08:00;Pilates"
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
                        redirect(t, "/aluno");
                        return;
                    }
                }
            }

            String html =
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<link rel='stylesheet' href='/style.css'>" +
                "<title>Login inválido</title>" +
                "</head>" +
                "<body>" +
                "<div class='login-container'>" +
                "<div class='login-box'>" +
                "<h2>Login inválido</h2>" +
                "<p>Usuário ou senha incorretos.</p>" +
                "<a href='/' class='btn-primary' style='width:100%; margin-top:15px;'>Tentar novamente</a>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";

            responder(t, html);
        }
    }

    static class SalvarHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            Map<String, String> dados = formToMap(t);

            List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));

            int id = alunos.size() + 1;

            String dataAula = dados.getOrDefault("dataAula", "");
            String horario = dados.getOrDefault("horario", "");
            String dataHorario = "";

            if (!dataAula.isEmpty() && !horario.isEmpty()) {
                dataHorario = dataAula + " " + horario;
            }

            String linha =
                id + ";" +
                dados.getOrDefault("nome", "") + ";" +
                dados.getOrDefault("patologia", "") + ";" +
                dados.getOrDefault("emDia", "false") + ";" +
                dados.getOrDefault("acesso", "nao") + ";" +
                dados.getOrDefault("usuario", "") + ";" +
                dados.getOrDefault("senha", "") + ";" +
                dados.getOrDefault("frequencia", "") + ";" +
                dados.getOrDefault("pontos", "0") + ";" +
                dados.getOrDefault("pagamento", "") + ";" +
                dados.getOrDefault("aulasRealizadas", "0") + ";" +
                dataHorario + ";" +
                dados.getOrDefault("modalidade", "");

            alunos.add(linha);

            Files.write(Paths.get(ARQUIVO), alunos);

            redirect(t, "/listar");
        }
    }

    static class ExcluirHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            String query = t.getRequestURI().getQuery();

            if (query == null || !query.contains("=")) {
                redirect(t, "/listar");
                return;
            }

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

                        String dataAula = "";
                        String horario = "";

                        if (p.length >= 12 && p[11].contains(" ")) {
                            String[] dh = p[11].split(" ");
                            dataAula = dh[0];
                            horario = dh.length > 1 ? dh[1] : "";
                        }

                        String html =
                            "<html>" +
                            "<head>" +
                            "<meta charset='UTF-8'>" +
                            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                            "<link rel='stylesheet' href='/style.css'>" +
                            "<title>Editar Aluno</title>" +
                            "</head>" +

                            "<body>" +
                            "<main class='main-content'>" +
                            "<div class='content-card'>" +
                            "<h1>Editar Aluno</h1>" +

                            "<form method='POST' action='/editar'>" +

                            "<input type='hidden' name='id' value='" + p[0] + "'>" +

                            "<label>Nome do aluno</label>" +
                            "<input type='text' name='nome' value='" + valor(p, 1) + "'>" +

                            "<label>Patologia</label>" +
                            "<input type='text' name='patologia' value='" + valor(p, 2) + "'>" +

                            "<label>Status do pagamento</label>" +
                            "<select name='emDia'>" +
                            "<option value='true'>Em dia</option>" +
                            "<option value='false'>Pendente</option>" +
                            "</select>" +

                            "<label>Permitir acesso ao sistema</label>" +
                            "<select name='acesso'>" +
                            "<option value='sim'>Sim</option>" +
                            "<option value='nao'>Não</option>" +
                            "</select>" +

                            "<label>Usuário do aluno</label>" +
                            "<input type='text' name='usuario' value='" + valor(p, 5) + "'>" +

                            "<label>Senha do aluno</label>" +
                            "<input type='password' name='senha' value='" + valor(p, 6) + "'>" +

                            "<label>Frequência semanal</label>" +
                            "<select name='frequencia'>" +
                            "<option>1x Semana</option>" +
                            "<option>2x Semana</option>" +
                            "<option>3x Semana</option>" +
                            "<option>4x Semana</option>" +
                            "<option>5x Semana</option>" +
                            "</select>" +

                            "<label>Pontos de fidelidade</label>" +
                            "<input type='number' name='pontos' value='" + valorOuPadrao(p, 8, "0") + "'>" +

                            "<label>Status financeiro</label>" +
                            "<select name='pagamento'>" +
                            "<option>Pago</option>" +
                            "<option>Pendente</option>" +
                            "<option>Atrasado</option>" +
                            "</select>" +

                            "<label>Quantidade de aulas realizadas</label>" +
                            "<input type='number' name='aulasRealizadas' value='" + valorOuPadrao(p, 10, "0") + "'>" +

                            "<label>Data da aula</label>" +
                            "<input type='date' name='dataAula' value='" + dataAula + "'>" +

                            "<label>Horário da aula</label>" +
                            "<input type='time' name='horario' value='" + horario + "'>" +

                            "<label>Modalidade</label>" +
                            "<select name='modalidade'>" +
                            "<option>Pilates</option>" +
                            "<option>Funcional</option>" +
                            "<option>Pilates e Funcional</option>" +
                            "</select>" +

                            "<br><br>" +
                            "<button class='btn-primary'>Salvar alterações</button>" +
                            "</form>" +

                            "</div>" +
                            "</main>" +
                            "</body>" +
                            "</html>";

                        responder(t, html);
                        return;
                    }
                }

            } else {

                Map<String, String> dados = formToMap(t);

                List<String> alunos = Files.readAllLines(Paths.get(ARQUIVO));
                List<String> novos = new ArrayList<>();

                String dataAula = dados.getOrDefault("dataAula", "");
                String horario = dados.getOrDefault("horario", "");
                String dataHorario = "";

                if (!dataAula.isEmpty() && !horario.isEmpty()) {
                    dataHorario = dataAula + " " + horario;
                }

                for (String aluno : alunos) {

                    String[] p = aluno.split(";");

                    if (p[0].equals(dados.get("id"))) {

                        novos.add(
                            p[0] + ";" +
                            dados.getOrDefault("nome", "") + ";" +
                            dados.getOrDefault("patologia", "") + ";" +
                            dados.getOrDefault("emDia", "false") + ";" +
                            dados.getOrDefault("acesso", "nao") + ";" +
                            dados.getOrDefault("usuario", "") + ";" +
                            dados.getOrDefault("senha", "") + ";" +
                            dados.getOrDefault("frequencia", "") + ";" +
                            dados.getOrDefault("pontos", "0") + ";" +
                            dados.getOrDefault("pagamento", "") + ";" +
                            dados.getOrDefault("aulasRealizadas", "0") + ";" +
                            dataHorario + ";" +
                            dados.getOrDefault("modalidade", "")
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

                linhas.append("<tr>")
                    .append("<td>").append(valor(p, 1)).append("</td>")
                    .append("<td>").append(valor(p, 2)).append("</td>")
                    .append("<td>").append(valor(p, 7)).append("</td>")
                    .append("<td>").append(valor(p, 8)).append("</td>")
                    .append("<td>").append(valor(p, 9)).append("</td>")
                    .append("<td>").append(valor(p, 10)).append("</td>")
                    .append("<td>").append(valor(p, 11)).append("</td>")
                    .append("<td>").append(valor(p, 12)).append("</td>")
                    .append("<td>")
                    .append("<a class='action-btn edit-btn' href='/editar?id=")
                    .append(valor(p, 0))
                    .append("'>Editar</a> ")
                    .append("<a class='action-btn delete-btn' href='/excluir?id=")
                    .append(valor(p, 0))
                    .append("'>Excluir</a>")
                    .append("</td>")
                    .append("</tr>");
            }

            String html =
                "<html>" +

                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
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
                "<a href='javascript:history.back()' class='nav-link'>Voltar</a>" +
                "<a href='/' class='nav-link logout-btn'>Sair</a>" +
                "</nav>" +

                "<main class='main-content'>" +

                "<div class='header alunos-header'>" +
                "<div>" +
                "<p class='data-text'>" + LocalDate.now() + "</p>" +
                "<h1>Alunos cadastrados</h1>" +
                "</div>" +

                "<div class='header-actions'>" +
                "<input type='text' id='buscarAluno' placeholder='Buscar aluno...' class='search-input'>" +
                "<a href='/cadastro' class='novo-btn'>+ Novo aluno</a>" +
                "</div>" +
                "</div>" +

                "<div class='content-card tabela-container'>" +
                "<div class='table-responsive'>" +

                "<table class='alunos-table'>" +

                "<thead>" +
                "<tr>" +
                "<th>Nome</th>" +
                "<th>Patologia</th>" +
                "<th>Frequência</th>" +
                "<th>Pontos</th>" +
                "<th>Pagamento</th>" +
                "<th>Aulas</th>" +
                "<th>Data e horário</th>" +
                "<th>Modalidade</th>" +
                "<th>Ações</th>" +
                "</tr>" +
                "</thead>" +

                "<tbody id='tabelaAlunos'>" +
                linhas +
                "</tbody>" +

                "</table>" +
                "</div>" +
                "</div>" +

                "<footer>" +
                "<p>&copy; 2026 Gindri Pilates. Todos os direitos reservados.</p>" +
                "</footer>" +

                "</main>" +

                "<script>" +
                "const busca = document.getElementById('buscarAluno');" +
                "busca.addEventListener('keyup', function() {" +
                "const texto = this.value.toLowerCase();" +
                "const linhasTabela = document.querySelectorAll('#tabelaAlunos tr');" +
                "linhasTabela.forEach(function(linha) {" +
                "const conteudo = linha.innerText.toLowerCase();" +
                "linha.style.display = conteudo.includes(texto) ? '' : 'none';" +
                "});" +
                "});" +
                "</script>" +

                "</body>" +
                "</html>";

            responder(t, html);
        }
    }

    static String valor(String[] p, int indice) {
        if (p.length > indice) {
            return p[indice];
        }
        return "";
    }

    static String valorOuPadrao(String[] p, int indice, String padrao) {
        if (p.length > indice && !p[indice].isEmpty()) {
            return p[indice];
        }
        return padrao;
    }

    static Map<String, String> formToMap(HttpExchange t) throws IOException {

        String form = new String(
            t.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        Map<String, String> dados = new HashMap<>();

        if (form.isEmpty()) {
            return dados;
        }

        for (String item : form.split("&")) {

            String[] p = item.split("=", 2);

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