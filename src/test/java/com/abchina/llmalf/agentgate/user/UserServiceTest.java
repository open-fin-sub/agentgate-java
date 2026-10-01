package com.abchina.llmalf.agentgate.user;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.abchina.llmalf.agentgate.user.service.IUserService;
import com.abchina.llmalf.agentgate.user.service.vo.UserQueryVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserSaveVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务集成测试.
 *
 * <p>连接本机真实 MySQL,每个用例独立事务并在结束后回滚,不污染数据.</p>
 * <p>覆盖:增、删、查、改 + 唯一性校验 + 逻辑删除.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private IUserService userService;

    private UserSaveVO buildSaveVO(String username) {
        UserSaveVO vo = new UserSaveVO();
        vo.setUsername(username);
        vo.setNickname("昵称_" + username);
        vo.setEmail(username + "@example.com");
        vo.setPhone("13800000000");
        vo.setStatus(1);
        return vo;
    }

    // ==================== 增 ====================

    @Test
    void testCreate_success() {
        Long id = userService.create(buildSaveVO("test_create_" + System.nanoTime()));
        Assertions.assertNotNull(id, "新增后应返回主键");
        Assertions.assertTrue(id > 0, "主键应为正数");
    }

    @Test
    void testCreate_duplicateUsername_shouldThrow() {
        String username = "test_dup_" + System.nanoTime();
        userService.create(buildSaveVO(username));
        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.create(buildSaveVO(username)));
        Assertions.assertTrue(ex.getMessage().contains("用户名已存在"));
    }

    // ==================== 查 ====================

    @Test
    void testGetById_success() {
        Long id = userService.create(buildSaveVO("test_get_" + System.nanoTime()));
        UserVO vo = userService.getById(id);
        Assertions.assertNotNull(vo, "查询结果不应为空");
        Assertions.assertEquals(id, vo.getId());
    }

    @Test
    void testGetById_notExist_shouldThrow() {
        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.getById(-99999L));
        Assertions.assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    void testPage_query() {
        String username = "test_page_" + System.nanoTime();
        userService.create(buildSaveVO(username));

        UserQueryVO query = new UserQueryVO();
        query.setUsername(username);
        query.setPageNum(1);
        query.setPageSize(10);
        IPage<UserVO> page = userService.page(query);

        Assertions.assertNotNull(page);
        Assertions.assertTrue(page.getTotal() >= 1, "应能查到刚插入的数据");
        Assertions.assertTrue(page.getRecords().stream()
                .anyMatch(u -> username.equals(u.getUsername())));
    }

    // ==================== 改 ====================

    @Test
    void testUpdate_success() {
        Long id = userService.create(buildSaveVO("test_upd_" + System.nanoTime()));

        UserSaveVO upd = buildSaveVO("test_upd_" + System.nanoTime());
        upd.setNickname("新昵称");
        upd.setEmail("new@example.com");
        userService.update(id, upd);

        UserVO vo = userService.getById(id);
        Assertions.assertEquals("新昵称", vo.getNickname());
        Assertions.assertEquals("new@example.com", vo.getEmail());
    }

    @Test
    void testUpdate_notExist_shouldThrow() {
        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.update(-99999L, buildSaveVO("ghost_" + System.nanoTime())));
        Assertions.assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    void testUpdate_duplicateUsername_shouldThrow() {
        String a = "test_upd_dup_a_" + System.nanoTime();
        String b = "test_upd_dup_b_" + System.nanoTime();
        Long idA = userService.create(buildSaveVO(a));
        userService.create(buildSaveVO(b));

        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.update(idA, buildSaveVO(b)));
        Assertions.assertTrue(ex.getMessage().contains("用户名已存在"));
    }

    // ==================== 删 ====================

    @Test
    void testDelete_success() {
        Long id = userService.create(buildSaveVO("test_del_" + System.nanoTime()));
        userService.delete(id);

        // 逻辑删除后查不到
        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.getById(id));
        Assertions.assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    void testDelete_notExist_shouldThrow() {
        AgentException ex = Assertions.assertThrows(AgentException.class,
                () -> userService.delete(-99999L));
        Assertions.assertTrue(ex.getMessage().contains("用户不存在"));
    }

    // ==================== 完整链路:增 -> 查 -> 改 -> 查 -> 删 -> 查 ====================

    @Test
    void testFullCrudFlow() {
        String username = "test_flow_" + System.nanoTime();

        // 增
        Long id = userService.create(buildSaveVO(username));
        Assertions.assertNotNull(id);

        // 查
        UserVO vo = userService.getById(id);
        Assertions.assertEquals(username, vo.getUsername());

        // 改
        UserSaveVO upd = buildSaveVO(username);
        upd.setNickname("流程昵称");
        userService.update(id, upd);
        Assertions.assertEquals("流程昵称", userService.getById(id).getNickname());

        // 查列表
        UserQueryVO query = new UserQueryVO();
        query.setUsername(username);
        Assertions.assertTrue(userService.page(query).getTotal() >= 1);

        // 删
        userService.delete(id);
        Assertions.assertThrows(AgentException.class, () -> userService.getById(id));
    }
}
