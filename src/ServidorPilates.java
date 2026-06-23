import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServidorPilates {

    static String alunoLogado = "";
    static int alunoLogadoId = 0;

    public static void main(String[] args) throws Exception {

        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        servidor.createContext("/style.css", new ArquivoHandler("style.css", "text/css"));
        servidor.createContext("/logo.png", new ArquivoHandler("logo.png", "image/png"));
        servidor.createContext("/studio.jpeg", new ArquivoHandler("studio.jpeg", "image/jpeg"));
        servidor.createContext("/pilatesfundo.jpg", new ArquivoHandler("pilatesfundo.jpg", "image/jpeg"));

        servidor.createContext("/", new ArquivoHandler("login.html", "text/html; charset=UTF-8"));
        servidor.createContext("/cadastro", new ArquivoHandler("cadastro.html", "text/html; charset=UTF-8"));

        servidor.createContext("/admin", new AdminHandler());
        servidor.createContext("/aluno", new AlunoHandler());
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

    static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
            System.getenv("DB_URL"),
            System.getenv("DB_USER"),
            System.getenv("DB_PASSWORD")
        );
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

            String usuario = dados.getOrDefault("usuario", "").trim();
            String senha = dados.getOrDefault("senha", "").trim();

            if (usuario.equalsIgnoreCase("alice") && senha.equals("123")) {
                redirect(t, "/admin");
                return;
            }

            String sql = "SELECT id, nome FROM alunos WHERE LOWER(usuario) = LOWER(?) AND senha = ?";

            try (Connection conn = conectar();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, usuario);
                stmt.setString(2, senha);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    alunoLogadoId = rs.getInt("id");
                    alunoLogado = rs.getString("nome");
                    redirect(t, "/aluno");
                    return;
                }

            } catch (Exception e) {
                responder(t, telaErro("Erro ao conectar no banco: " + e.getMessage()));
                return;
            }

            responder(t, telaLoginInvalido());
        }
    }

    static class SalvarHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            Map<String, String> dados = formToMap(t);

            String dataAula = dados.getOrDefault("dataAula", "");
            String horario = dados.getOrDefault("horario", "");
            String modalidade = dados.getOrDefault("modalidade", "");

            String dataHorario = "";
            if (!dataAula.isEmpty() && !horario.isEmpty()) {
                dataHorario = dataAula + " " + horario;
            }

            String sqlAluno =
                "INSERT INTO alunos " +
                "(nome, patologia, pagamento_em_dia, usuario, senha, frequencia, pontos, pagamento, aulas_realizadas, datas_aulas, modalidades, home_care) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = conectar();
                 PreparedStatement stmt = conn.prepareStatement(sqlAluno, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, dados.getOrDefault("nome", ""));
                stmt.setString(2, dados.getOrDefault("patologia", ""));
                stmt.setBoolean(3, Boolean.parseBoolean(dados.getOrDefault("emDia", "false")));
                stmt.setString(4, dados.getOrDefault("usuario", ""));
                stmt.setString(5, dados.getOrDefault("senha", ""));
                stmt.setString(6, dados.getOrDefault("frequencia", ""));
                stmt.setInt(7, inteiro(dados.getOrDefault("pontos", "0")));
                stmt.setString(8, dados.getOrDefault("pagamento", ""));
                stmt.setInt(9, inteiro(dados.getOrDefault("aulasRealizadas", "0")));
                stmt.setString(10, dataHorario);
                stmt.setString(11, modalidade);
                stmt.setString(12, dados.getOrDefault("homeCare", ""));

                stmt.executeUpdate();

                ResultSet ids = stmt.getGeneratedKeys();

                if (ids.next() && !dataAula.isEmpty() && !horario.isEmpty()) {
                    int alunoId = ids.getInt(1);
                    salvarAula(conn, alunoId, dataAula, horario, modalidade);
                }

                redirect(t, "/listar");

            } catch (Exception e) {
                responder(t, telaErro("Erro ao salvar aluno: " + e.getMessage()));
            }
        }
    }

    static class ExcluirHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            String query = t.getRequestURI().getQuery();

            if (query == null || !query.contains("=")) {
                redirect(t, "/listar");
                return;
            }

            int id = inteiro(query.split("=")[1]);

            try (Connection conn = conectar();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM alunos WHERE id = ?")) {

                stmt.setInt(1, id);
                stmt.executeUpdate();

                redirect(t, "/listar");

            } catch (Exception e) {
                responder(t, telaErro("Erro ao excluir aluno: " + e.getMessage()));
            }
        }
    }

    static class EditarHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            if (t.getRequestMethod().equals("GET")) {

                String id = t.getRequestURI().getQuery().split("=")[1];

                String sql =
                    "SELECT a.*, au.data_aula, au.horario, au.modalidade AS modalidade_aula " +
                    "FROM alunos a " +
                    "LEFT JOIN aulas au ON au.aluno_id = a.id " +
                    "WHERE a.id = ? " +
                    "LIMIT 1";

                try (Connection conn = conectar();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, inteiro(id));

                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        responder(t, telaEditar(rs));
                        return;
                    }

                    responder(t, telaErro("Aluno não encontrado."));

                } catch (Exception e) {
                    responder(t, telaErro("Erro ao abrir edição: " + e.getMessage()));
                }

            } else {

                Map<String, String> dados = formToMap(t);

                int id = inteiro(dados.getOrDefault("id", "0"));

                String dataAula = dados.getOrDefault("dataAula", "");
                String horario = dados.getOrDefault("horario", "");
                String modalidade = dados.getOrDefault("modalidade", "");

                String dataHorario = "";
                if (!dataAula.isEmpty() && !horario.isEmpty()) {
                    dataHorario = dataAula + " " + horario;
                }

                String sql =
                    "UPDATE alunos SET " +
                    "nome = ?, patologia = ?, pagamento_em_dia = ?, usuario = ?, senha = ?, frequencia = ?, " +
                    "pontos = ?, pagamento = ?, aulas_realizadas = ?, datas_aulas = ?, modalidades = ?, home_care = ? " +
                    "WHERE id = ?";

                try (Connection conn = conectar();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, dados.getOrDefault("nome", ""));
                    stmt.setString(2, dados.getOrDefault("patologia", ""));
                    stmt.setBoolean(3, Boolean.parseBoolean(dados.getOrDefault("emDia", "false")));
                    stmt.setString(4, dados.getOrDefault("usuario", ""));
                    stmt.setString(5, dados.getOrDefault("senha", ""));
                    stmt.setString(6, dados.getOrDefault("frequencia", ""));
                    stmt.setInt(7, inteiro(dados.getOrDefault("pontos", "0")));
                    stmt.setString(8, dados.getOrDefault("pagamento", ""));
                    stmt.setInt(9, inteiro(dados.getOrDefault("aulasRealizadas", "0")));
                    stmt.setString(10, dataHorario);
                    stmt.setString(11, modalidade);
                    stmt.setString(12, dados.getOrDefault("homeCare", ""));
                    stmt.setInt(13, id);

                    stmt.executeUpdate();

                    apagarAulasDoAluno(conn, id);

                    if (!dataAula.isEmpty() && !horario.isEmpty()) {
                        salvarAula(conn, id, dataAula, horario, modalidade);
                    }

                    redirect(t, "/listar");

                } catch (Exception e) {
                    responder(t, telaErro("Erro ao atualizar aluno: " + e.getMessage()));
                }
            }
        }
    }

    static class ListarHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            StringBuilder linhas = new StringBuilder();

            String sql =
                "SELECT a.*, au.data_aula, au.horario, au.modalidade AS modalidade_aula " +
                "FROM alunos a " +
                "LEFT JOIN aulas au ON au.aluno_id = a.id " +
                "ORDER BY a.id DESC";

            try (Connection conn = conectar();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    String dataHorario = "";

                    Date data = rs.getDate("data_aula");
                    Time hora = rs.getTime("horario");

                    if (data != null && hora != null) {
                        dataHorario = data.toString() + " " + hora.toString().substring(0, 5);
                    } else {
                        dataHorario = texto(rs, "datas_aulas");
                    }

                    String modalidade = texto(rs, "modalidade_aula");
                    if (modalidade.isEmpty()) {
                        modalidade = texto(rs, "modalidades");
                    }

                    linhas.append("<tr>")
                        .append("<td>").append(texto(rs, "nome")).append("</td>")
                        .append("<td>").append(texto(rs, "patologia")).append("</td>")
                        .append("<td>").append(texto(rs, "frequencia")).append("</td>")
                        .append("<td>").append(rs.getInt("pontos")).append("</td>")
                        .append("<td>").append(texto(rs, "pagamento")).append("</td>")
                        .append("<td>").append(rs.getInt("aulas_realizadas")).append("</td>")
                        .append("<td>").append(dataHorario).append("</td>")
                        .append("<td>").append(modalidade).append("</td>")
                        .append("<td>")
                        .append("<a class='action-btn edit-btn' href='/editar?id=")
                        .append(rs.getInt("id"))
                        .append("'>Editar</a> ")
                        .append("<a class='action-btn delete-btn' href='/excluir?id=")
                        .append(rs.getInt("id"))
                        .append("'>Excluir</a>")
                        .append("</td>")
                        .append("</tr>");
                }

                responder(t, telaListar(linhas.toString()));

            } catch (Exception e) {
                responder(t, telaErro("Erro ao listar alunos: " + e.getMessage()));
            }
        }
    }

    static class AdminHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            try (Connection conn = conectar()) {

                int alunosAtivos = contar(conn, "SELECT COUNT(*) FROM alunos");
                int inadimplentes = contar(conn, "SELECT COUNT(*) FROM alunos WHERE pagamento_em_dia = false");
                int aulasHoje = contar(conn, "SELECT COUNT(*) FROM aulas WHERE data_aula = CURDATE()");
                int concluidas = contar(conn, "SELECT COALESCE(SUM(aulas_realizadas), 0) FROM alunos");

                StringBuilder agenda = new StringBuilder();

                String sql =
                    "SELECT a.nome, au.horario, au.modalidade " +
                    "FROM aulas au " +
                    "JOIN alunos a ON a.id = au.aluno_id " +
                    "WHERE au.data_aula = CURDATE() " +
                    "ORDER BY au.horario";

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String hora = rs.getTime("horario").toString().substring(0, 5);

                        agenda.append("<tr>")
                            .append("<td>").append(hora).append("</td>")
                            .append("<td>").append(texto(rs, "nome")).append("</td>")
                            .append("<td>").append(texto(rs, "modalidade")).append("</td>")
                            .append("<td><span class='badge badge-success'>Agendada</span></td>")
                            .append("</tr>");
                    }
                }

                if (agenda.length() == 0) {
                    agenda.append("<tr><td colspan='4'>Nenhuma aula agendada para hoje.</td></tr>");
                }

                responder(t, telaAdmin(alunosAtivos, aulasHoje, inadimplentes, concluidas, agenda.toString()));

            } catch (Exception e) {
                responder(t, telaErro("Erro ao carregar painel: " + e.getMessage()));
            }
        }
    }

    static class AlunoHandler implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {

            if (alunoLogadoId == 0) {
                redirect(t, "/");
                return;
            }

            String sql = "SELECT * FROM alunos WHERE id = ?";

            try (Connection conn = conectar();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, alunoLogadoId);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    responder(t, telaAluno(rs));
                    return;
                }

                redirect(t, "/");

            } catch (Exception e) {
                responder(t, telaErro("Erro ao carregar área do aluno: " + e.getMessage()));
            }
        }
    }

    static void salvarAula(Connection conn, int alunoId, String dataAula, String horario, String modalidade) throws SQLException {

        String sql = "INSERT INTO aulas (aluno_id, data_aula, horario, modalidade) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alunoId);
            stmt.setDate(2, Date.valueOf(dataAula));
            stmt.setTime(3, Time.valueOf(horario + ":00"));
            stmt.setString(4, modalidade);
            stmt.executeUpdate();
        }
    }

    static void apagarAulasDoAluno(Connection conn, int alunoId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM aulas WHERE aluno_id = ?")) {
            stmt.setInt(1, alunoId);
            stmt.executeUpdate();
        }
    }

    static int contar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

            return 0;
        }
    }

    static String telaAdmin(int ativos, int aulasHoje, int inadimplentes, int concluidas, String agenda) {

        return "<html>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<link rel='stylesheet' href='/style.css'>" +
            "<title>Painel Admin - Gindri Pilates</title>" +
            "</head>" +

            "<body>" +
            "<nav class='sidebar'>" +
            "<div class='logo-container'><img src='/logo.png' alt='Logo'></div>" +
            "<a href='/admin' class='nav-link active'>Painel</a>" +
            "<a href='/listar' class='nav-link'>Alunos</a>" +
            "<a href='/cadastro' class='nav-link'>Novo Cadastro</a>" +
            "<a href='/' class='nav-link logout-btn'>Sair</a>" +
            "</nav>" +

            "<main class='main-content'>" +
            "<div class='header admin-hero'>" +
            "<div>" +
            "<p class='data-text'>" +
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern(
                    "EEEE, dd 'de' MMMM 'de' yyyy",
                    new Locale("pt", "BR")
                ))
                .replace("segunda-feira", "Segunda-Feira")
                .replace("terça-feira", "Terça-Feira")
                .replace("quarta-feira", "Quarta-Feira")
                .replace("quinta-feira", "Quinta-Feira")
                .replace("sexta-feira", "Sexta-Feira")
                .replace("sábado", "Sábado")
                .replace("domingo", "Domingo")
                .replace(" de ", " De ")
            + "</p>" +            "<h1>Bem-vinda, Alice</h1>" +
            "</div>" +
            "</div>" +

            "<div class='stats-grid'>" +
            "<div class='stat-card'><small>Alunos ativos</small><div>" + ativos + "</div></div>" +
            "<div class='stat-card'><small>Aulas hoje</small><div>" + aulasHoje + "</div></div>" +
            "<div class='stat-card'><small>Inadimplentes</small><div style='color: var(--danger)'>" + inadimplentes + "</div></div>" +
            "<div class='stat-card'><small>Aulas concluídas</small><div>" + concluidas + "</div></div>" +
            "</div>" +

            "<div class='content-card'>" +
            "<h2>Agenda de hoje</h2>" +
            "<div class='table-responsive'>" +
            "<table>" +
            "<thead><tr><th>Horário</th><th>Nome</th><th>Modalidade</th><th>Status</th></tr></thead>" +
            "<tbody>" + agenda + "</tbody>" +
            "</table>" +
            "</div>" +
            "</div>" +

            "<footer><p>&copy; 2026 Gindri Pilates. Todos os direitos reservados.</p></footer>" +
            "</main>" +
            "</body>" +
            "</html>";
    }

    static String telaAluno(ResultSet rs) throws SQLException {

        String pagamento = texto(rs, "pagamento");

        String badge = "badge-success";
        if (pagamento.equalsIgnoreCase("Atrasado") || pagamento.equalsIgnoreCase("Pendente")) {
            badge = "badge-danger";
        }

        String homeCare = texto(rs, "home_care");

        if (homeCare.isEmpty()) {
            homeCare = "Nenhum exercício Home Care foi cadastrado ainda.";
        }

        homeCare = homeCare
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\r\n", "<br>")
            .replace("\n", "<br>");

        return "<html>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<link rel='stylesheet' href='/style.css'>" +
            "<title>Área do Aluno - Gindri Pilates</title>" +
            "</head>" +

            "<body>" +
            "<nav class='sidebar'>" +
            "<div class='logo-container'><img src='/logo.png' alt='Logo'></div>" +
            "<a href='/aluno' class='nav-link active'>Minha Área</a>" +
            "<a href='/' class='nav-link logout-btn'>Sair</a>" +
            "</nav>" +

            "<main class='main-content'>" +
            "<div class='header'>" +
            "<div>" +
            "<p class='data-text'>" +
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern(
                    "EEEE, dd 'de' MMMM 'de' yyyy",
                    new Locale("pt", "BR")
                ))
                .replace("segunda-feira", "Segunda-Feira")
                .replace("terça-feira", "Terça-Feira")
                .replace("quarta-feira", "Quarta-Feira")
                .replace("quinta-feira", "Quinta-Feira")
                .replace("sexta-feira", "Sexta-Feira")
                .replace("sábado", "Sábado")
                .replace("domingo", "Domingo")
                .replace(" de ", " De ")
            + "</p>" +
            "<h1>Olá, " + texto(rs, "nome") + "</h1>" +
            "</div>" +
            "</div>" +

            "<div class='stats-grid'>" +
            "<div class='stat-card'><small>Aulas realizadas</small><div>" + rs.getInt("aulas_realizadas") + "</div></div>" +
            "<div class='stat-card'><small>Frequência semanal</small><div style='color: var(--secondary)'>" + texto(rs, "frequencia") + "</div></div>" +
            "<div class='stat-card'><small>Pagamento</small><div style='margin-top:10px;'><span class='badge " + badge + "'>" + pagamento + "</span></div></div>" +
            "<div class='stat-card'><small>Pontos fidelidade</small><div>" + rs.getInt("pontos") + "</div></div>" +
            "</div>" +

            "<div class='content-card'>" +
            "<h3>Exercícios Home Care</h3>" +
            "<p style='color:#64748b;'>Exercícios indicados pela instrutora:</p>" +
            "<div style='margin-top:20px; border-left:4px solid var(--secondary); padding-left:15px; line-height:1.8;'>" +
            homeCare +
            "</div>" +
            "</div>" +

            "<footer><p>&copy; 2026 Gindri Pilates. Todos os direitos reservados.</p></footer>" +
            "</main>" +
            "</body>" +
            "</html>";
    }

    static String telaEditar(ResultSet rs) throws SQLException {

        String dataAula = "";
        String horario = "";

        Date data = rs.getDate("data_aula");
        Time hora = rs.getTime("horario");

        if (data != null) {
            dataAula = data.toString();
        }

        if (hora != null) {
            horario = hora.toString().substring(0, 5);
        }

        String modalidade = texto(rs, "modalidade_aula");
        if (modalidade.isEmpty()) {
            modalidade = texto(rs, "modalidades");
        }

        return "<html>" +
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
            "<input type='hidden' name='id' value='" + rs.getInt("id") + "'>" +

            "<label>Nome do aluno</label>" +
            "<input type='text' name='nome' value='" + texto(rs, "nome") + "'>" +

            "<label>Patologia</label>" +
            "<input type='text' name='patologia' value='" + texto(rs, "patologia") + "'>" +

            "<label>Status do pagamento</label>" +
            "<select name='emDia'>" +
            "<option value='true'>Em dia</option>" +
            "<option value='false'>Pendente</option>" +
            "</select>" +

            "<label>Usuário do aluno</label>" +
            "<input type='text' name='usuario' value='" + texto(rs, "usuario") + "'>" +

            "<label>Senha do aluno</label>" +
            "<input type='password' name='senha' value='" + texto(rs, "senha") + "'>" +

            "<label>Frequência semanal</label>" +
            "<select name='frequencia'>" +
            "<option>" + texto(rs, "frequencia") + "</option>" +
            "<option>1x Semana</option>" +
            "<option>2x Semana</option>" +
            "<option>3x Semana</option>" +
            "<option>4x Semana</option>" +
            "<option>5x Semana</option>" +
            "</select>" +

            "<label>Pontos de fidelidade</label>" +
            "<input type='number' name='pontos' value='" + rs.getInt("pontos") + "'>" +

            "<label>Status financeiro</label>" +
            "<select name='pagamento'>" +
            "<option>" + texto(rs, "pagamento") + "</option>" +
            "<option>Pago</option>" +
            "<option>Pendente</option>" +
            "<option>Atrasado</option>" +
            "</select>" +

            "<label>Quantidade de aulas realizadas</label>" +
            "<input type='number' name='aulasRealizadas' value='" + rs.getInt("aulas_realizadas") + "'>" +

            "<label>Data da aula</label>" +
            "<input type='date' name='dataAula' value='" + dataAula + "'>" +

            "<label>Horário da aula</label>" +
            "<input type='time' name='horario' value='" + horario + "'>" +

            "<label>Modalidade</label>" +
            "<select name='modalidade'>" +
            "<option>" + modalidade + "</option>" +
            "<option>Pilates</option>" +
            "<option>Funcional</option>" +
            "<option>Pilates e Funcional</option>" +
            "</select>" +

            "<label>Exercícios Home Care</label>" +
            "<textarea name='homeCare' rows='6' placeholder='Ex: Alongamento de cadeia posterior - 3 séries de 45 segundos'>" +
            texto(rs, "home_care") +
            "</textarea>" +

            "<br><br>" +
            "<button class='btn-primary'>Salvar alterações</button>" +
            "</form>" +
            "</div>" +
            "</main>" +
            "</body>" +
            "</html>";
    }

    static String telaListar(String linhas) {

        return "<html>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<link rel='stylesheet' href='/style.css'>" +
            "<title>Alunos</title>" +
            "</head>" +

            "<body>" +

            "<nav class='sidebar'>" +
            "<div class='logo-container'><img src='/logo.png'></div>" +
            "<a href='/admin' class='nav-link'>Painel</a>" +
            "<a href='/listar' class='nav-link active'>Alunos</a>" +
            "<a href='/cadastro' class='nav-link'>Novo Cadastro</a>" +
            "<a href='/' class='nav-link logout-btn'>Sair</a>" +
            "</nav>" +

            "<main class='main-content'>" +

            "<div class='page-actions'>" +
            "<a href='/admin' class='back-btn'>Voltar ao Painel</a>" +
            "</div>" +

            "<div class='admin-hero'>" +

            "<p class='data-text'>" +
            java.time.LocalDate.now()
            .format(
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            ) +
            "</p>" +

            "<h1>Alunos Cadastrados</h1>" +

            "<p class='subtitle'>" +
            "Gerencie os alunos, horários e informações cadastradas no sistema." +
            "</p>" +

            "</div>" +

            "<div class='header-actions' style='margin-bottom:20px;'>" +
            "<input type='text' id='buscarAluno' placeholder='Buscar aluno...' class='search-input'>" +
            "<a href='/cadastro' class='novo-btn'>Novo Aluno</a>" +
            "</div>" +

            "<div class='content-card'>" +
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
            "<tbody id='tabelaAlunos'>" + linhas + "</tbody>" +
            "</table>" +
            "</div>" +
            "</div>" +

            "<footer><p>&copy; 2026 Gindri Pilates. Todos os direitos reservados.</p></footer>" +
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
    }

    static String telaLoginInvalido() {
        return "<html>" +
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
    }

    static String telaErro(String mensagem) {
        return "<html>" +
            "<head>" +
            "<meta charset='UTF-8'>" +
            "<link rel='stylesheet' href='/style.css'>" +
            "<title>Erro</title>" +
            "</head>" +
            "<body>" +
            "<main class='main-content'>" +
            "<div class='content-card'>" +
            "<h1>Erro</h1>" +
            "<p>" + mensagem + "</p>" +
            "<br>" +
            "<a class='btn-primary' href='/admin'>Voltar</a>" +
            "</div>" +
            "</main>" +
            "</body>" +
            "</html>";
    }

    static String texto(ResultSet rs, String coluna) throws SQLException {
        String valor = rs.getString(coluna);
        return valor == null ? "" : valor;
    }

    static int inteiro(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (Exception e) {
            return 0;
        }
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