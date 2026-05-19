package com.javademo1.service;

import com.javademo1.dao.ActivityRepository;
import com.javademo1.dao.MessageRepository;
import com.javademo1.dao.PostRepository;
import com.javademo1.dao.UserRepository;
import com.javademo1.pojo.Message;
import com.javademo1.pojo.User;
import com.javademo1.pojo.user.PasswordUpdateRequest;
import com.javademo1.pojo.user.UserProfileUpdateRequest;
import com.javademo1.util.BizException;
import com.javademo1.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final ActivityRepository activityRepository;
    private final PasswordEncoder passwordEncoder;

    public User profile(CurrentUser currentUser) {
        return userRepository.findById(currentUser.id()).orElseThrow(() -> new BizException("用户不存在"));
    }

    public User updateProfile(CurrentUser currentUser, UserProfileUpdateRequest request) {
        User user = profile(currentUser);

        String phone = request.getPhone() == null ? null : request.getPhone().trim();
        if (phone == null || phone.isBlank()) {
            user.setPhone(null);
        } else {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new BizException("手机号格式不正确，请输入 11 位手机号");
            }
            if (!phone.equals(user.getPhone())) {
                userRepository.findByPhone(phone).ifPresent(exist -> {
                    throw new BizException("手机号已被占用");
                });
            }
            user.setPhone(phone);
        }

        user.setAvatar(request.getAvatar());
        user.setGender(request.getGender());
        user.setSkateStyle(request.getSkateStyle());
        user.setBio(request.getBio());
        return userRepository.save(user);
    }

    public void updatePassword(CurrentUser currentUser, PasswordUpdateRequest request) {
        User user = profile(currentUser);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public List<Message> myMessages(CurrentUser currentUser) {
        return messageRepository.findByUserIdOrderByCreateTimeDesc(currentUser.id());
    }

    public void readMessage(CurrentUser currentUser, Long messageId) {
        Message message = messageRepository.findById(messageId).orElseThrow(() -> new BizException("消息不存在"));
        if (!message.getUserId().equals(currentUser.id())) {
            throw new BizException("无权操作该消息");
        }
        message.setIsRead("1");
        messageRepository.save(message);
    }

    public Map<String, Object> dashboard(CurrentUser currentUser) {
        Map<String, Object> map = new HashMap<>();
        map.put("postCount", postRepository.countByUserId(currentUser.id()));
        map.put("activityCount", activityRepository.countByUserId(currentUser.id()));
        map.put("unreadMsgCount", messageRepository.countByUserIdAndIsRead(currentUser.id(), "0"));
        return map;
    }
}
