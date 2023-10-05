package com.example.service;

import com.example.config.BotConfig;
import com.example.model.User;
import com.example.model.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private UserRepository userRepository;
    final BotConfig config;
    private final String ERROR_OCCURRED = "Error occurred: ";
    private final String MESSAGE_RECEIVED = "Received message from user: ";
    private final String USER_SAVED = "User saved in db: ";
    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            log.info(MESSAGE_RECEIVED+update.getMessage().getChat().getUserName()+", message: " + message);
            long chatId = update.getMessage().getChatId();
            if (message.equalsIgnoreCase("/start")) {
                registerUser(update.getMessage());
                startCommandReceived(chatId, update.getMessage().getChat().getUserName());
            } else if (message.contains("weather")) {
                String answer = "Что-то пошло не так";
                String city = "New York";
                String[] array = message.split(" ");
                if (array.length == 2) {
                    city = array[1];
                }
                try {
                    answer = prepareWeatherForecast(city);
                } catch (Exception e) {
                log.error(ERROR_OCCURRED+e.getMessage());
                }
                sendMessage(chatId, answer);
            } else {
                sendMessage(chatId, "Sorry ....");
            }
        }

    }

    private void registerUser(Message message) {
    if (userRepository.findById(message.getChatId()).isEmpty()){
        var chatId = message.getChatId();
        var chat = message.getChat();
        User user = new User();
        user.setChatId(chatId);
        user.setUserName(chat.getUserName());
        user.setFirstName(chat.getFirstName());
        user.setLastName(chat.getLastName());
        user.setChatLocation(chat.getLocation());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        userRepository.save(user);
        log.info(USER_SAVED+user.toString());
    }

    }

    private String prepareWeatherForecast(String city) throws IOException {
        final DateTimeFormatter INPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        final DateTimeFormatter OUTPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE dd MMMM HH:mm", Locale.US);
        Map<String, String> map = new HashMap<>();
        map.put("clear sky", "Солнечно 🌞️");
        map.put("scattered clouds", "Облачно 🌥️");
        map.put("few clouds", "Малооблачно 🌤️");
        map.put("overcast clouds", "Пасмурно ⛅");
        map.put("broken clouds", "Пасмурно ⛅");
        map.put("light rain", "Лёгкий дождь 🌦️");


        //--------------------------------------------------------------------------------------
        // Создание HTTP запроса
        final String API_CALL_TEMPLATE = "https://api.openweathermap.org/data/2.5/forecast?q=";
        final String API_KEY_TEMPLATE = "&APPID=658841465c89239aed6eef0c23849bc4";
        StringBuffer stringBuffer = new StringBuffer();
        String urlString = API_CALL_TEMPLATE + city + API_KEY_TEMPLATE;
        URL urlObject = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) urlObject.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        // connection.getContentType();
        int responseCode = connection.getResponseCode();
        if (responseCode == 404) {
            throw new IllegalArgumentException();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        //--------------------------------------------------------------------------------------
        // Обработка JSON ответа от сервера
        String data = response.toString();
        String cityForForecast = new ObjectMapper().readTree(data).get("city").get("name").toString();
        JsonNode arrNode = new ObjectMapper().readTree(data).get("list");
        StringBuilder sb = new StringBuilder();
        sb.append("***Погода на 5 дней для города " + cityForForecast + ":***\n");

        for (JsonNode node : arrNode) {
            String str = node.get("dt_txt").toString();
            if (str.contains("09:00:00") || str.contains("15:00:00") || str.contains("21:00:00")) {
                String date = node.get("dt_txt").toString().replaceAll("\"", "");
                LocalDateTime localDateTime = LocalDateTime.parse(date, INPUT_DATE_TIME_FORMAT);
                sb.append(localDateTime.getDayOfMonth() + " ");
                sb.append(Month.valueOf(localDateTime.getMonth().toString()).getName() + ", ");
                sb.append(DayOfWeek.valueOf(localDateTime.getDayOfWeek().toString()).getName() + ", ");
                sb.append("Время " + localDateTime.getHour() + " часов,");
                sb.append("\n");
                int temperature = (int) Math.round((node.get("main").get("temp").asDouble() - 273.15));
                sb.append(" Температура: " + temperature + "℃,");
                String description = node.get("weather").get(0).get("description").toString();
                description = description.replaceAll("\"", "");
                sb.append(" " + map.get(description));
                sb.append("\n");
            }
        }
        return sb.toString();
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    public void startCommandReceived(long chatId, String name) {
        String answer = "Привет," + name + ". Для получения информации о погоде, введи: weather [название города латиницей]";
        sendMessage(chatId, answer);
    }

    public void sendMessage(long chatID, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatID));
        sendMessage.setText(message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }
}
