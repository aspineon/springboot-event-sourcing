package com.hendisantika.eda.springbooteventsourcing.controller;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hendisantika.eda.springbooteventsourcing.domain.User;
import com.hendisantika.eda.springbooteventsourcing.event.EventStoreContainer;
import com.hendisantika.eda.springbooteventsourcing.event.UserCreatedEvent;
import com.hendisantika.eda.springbooteventsourcing.event.UserDeactivatedEvent;
import com.hendisantika.eda.springbooteventsourcing.event.UserVerifiedEvent;
import com.hendisantika.eda.springbooteventsourcing.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * Project : springboot-event-sourcing
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 2019-04-24
 * Time: 07:09
 */
@RequestMapping("/user")
@Controller
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    RedisTemplate<String, EventStoreContainer> redisTemplate;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    HazelcastInstance hazelcastInstance;

    @GetMapping
    public String index(Model model) {
        Config cc = new Config();

        Collection userList = hazelcastInstance.getMap("userList").values();
        Collection deactivatedUsers = hazelcastInstance.getMap("deactivatedUsers").values();
        Collection verifiedUsers = hazelcastInstance.getMap("verifiedUsers").values();

        model.addAttribute("verifiedUsers", verifiedUsers);
        model.addAttribute("deactivatedUsers", deactivatedUsers);
        model.addAttribute("users", userList);
        model.addAttribute("user", new User());
        return "index";
    }

    @PostMapping("/create")
    @Transactional
    public String create(@ModelAttribute User user, Model model) {
        Long eventId = redisTemplate.opsForValue().increment("event_ids", 1);
        Long aggregateId = redisTemplate.opsForValue().increment("user_ids", 1);
        user.setId(aggregateId);
        UserCreatedEvent userCreatedEvent = new UserCreatedEvent(eventId, user);

        EventStoreContainer eventStoreContainer = new EventStoreContainer(userCreatedEvent);
        Long nextIndex = redisTemplate.opsForList().rightPush("events", eventStoreContainer);

        stringRedisTemplate.convertAndSend("new_events", Long.toString(nextIndex - 1));
        return "redirect:/user";
    }

    @PostMapping("/verify")
    @Transactional
    public String verify(@ModelAttribute User user, Model model) {
        Long eventId = redisTemplate.opsForValue().increment("event_ids", 1);
        UserVerifiedEvent userVerifiedEvent = new UserVerifiedEvent(eventId, user.getId(), "mploed");
        Long nextIndex = redisTemplate.opsForList().rightPush("events", new EventStoreContainer(userVerifiedEvent));

        stringRedisTemplate.convertAndSend("new_events", Long.toString(nextIndex - 1));
        return "redirect:/user";
    }

    @PostMapping("/deactivate")
    @Transactional
    public String deactivate(@ModelAttribute User user, Model model) {
        Long eventId = redisTemplate.opsForValue().increment("event_ids", 1);
        UserDeactivatedEvent userDeactivatedEvent = new UserDeactivatedEvent(eventId, user.getId(), "mploed");
        Long nextIndex = redisTemplate.opsForList().rightPush("events", new EventStoreContainer(userDeactivatedEvent));

        stringRedisTemplate.convertAndSend("new_events", Long.toString(nextIndex - 1));
        return "redirect:/user";
    }

    @PostMapping("/replay")
    public String replay() {
        hazelcastInstance.getMap("userList").clear();
        Long size = redisTemplate.opsForList().size("events");
        for (int i = 0; i < size; i++) {
            stringRedisTemplate.convertAndSend("new_events", Integer.toString(i));
        }
        return "redirect:/user";
    }

}
