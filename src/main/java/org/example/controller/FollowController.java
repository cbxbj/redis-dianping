package org.example.controller;

import jakarta.annotation.Resource;
import org.example.dto.Result;
import org.example.service.IFollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注
     *
     * @param followUserId 要关注/取关的用户id
     * @param isFollow     关注/取关
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 是否关注
     *
     * @param followUserId 当前用户是否关注该用户
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }


    /**
     * 当前用户与指定用户的共同关注好友
     *
     * @param id 指定用户id
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }
}
