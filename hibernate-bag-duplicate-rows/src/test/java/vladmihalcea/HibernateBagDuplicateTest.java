package vladmihalcea;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vladmihalcea.hibernatebagduplicaterows.model.Child;
import vladmihalcea.hibernatebagduplicaterows.model.Parent;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/applicatonContext.xml"})
public class HibernateBagDuplicateTest {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateBagDuplicateTest.class);

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void init() {
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    @Test
    public void test() {

        final Long parentId = cleanAndSaveParent();

        Parent parent = transactionTemplate.execute(new TransactionCallback<Parent>() {
            @Override
            public Parent doInTransaction(TransactionStatus transactionStatus) {
                Parent parent = entityManager.createQuery("from Parent where id =:parentId", Parent.class).setParameter("parentId", parentId).getSingleResult();
                Child child1 = new Child();
                child1.setName("child1");
                Child child2 = new Child();
                child2.setName("child2");
                parent.addChild(child1);
                parent.addChild(child2);
                entityManager.merge(parent);
                parent.getChildren().size();
                return parent;
            }
        });
        //https://hibernate.atlassian.net/browse/HHH-3332
        //https://hibernate.atlassian.net/browse/HHH-5855
        //assertEquals(2, parent.getChildren().size());
        if(parent.getChildren().size() == 4) {
            LOG.error("Duplicates rows generated!");
        }
    }

    @Test
    public void testFixByPersistingChild() {
        final Long parentId = cleanAndSaveParent();

        Parent parent = transactionTemplate.execute(new TransactionCallback<Parent>() {
            @Override
            public Parent doInTransaction(TransactionStatus transactionStatus) {
                Parent parent = entityManager.createQuery("from Parent where id =:parentId", Parent.class).setParameter("parentId", parentId).getSingleResult();
                Child child1 = new Child();
                child1.setName("child1");
                Child child2 = new Child();
                child2.setName("child2");
                entityManager.persist(child1);
                entityManager.persist(child2);
                parent.addChild(child1);
                parent.addChild(child2);
                entityManager.merge(parent);
                parent.getChildren().size();
                return parent;
            }
        });
        assertEquals(2, parent.getChildren().size());
    }

    @Test
    public void testFixByFlushingOnCommit() {
        final Long parentId = cleanAndSaveParent();

        Parent parent = transactionTemplate.execute(new TransactionCallback<Parent>() {
            @Override
            public Parent doInTransaction(TransactionStatus transactionStatus) {
                Parent parent = entityManager.createQuery("from Parent where id =:parentId", Parent.class).setParameter("parentId", parentId).getSingleResult();
                parent.getChildren().size();
                Child child1 = new Child();
                child1.setName("child1");
                Child child2 = new Child();
                child2.setName("child2");
                parent.addChild(child1);
                parent.addChild(child2);
                return parent;
            }
        });
        assertEquals(2, parent.getChildren().size());
    }

    protected Long cleanAndSaveParent() {
        return transactionTemplate.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus transactionStatus) {
                entityManager.createQuery("delete from Child").executeUpdate();
                entityManager.createQuery("delete from Parent").executeUpdate();
                assertTrue(entityManager.createQuery("from Parent").getResultList().isEmpty());
                Parent parent = new Parent();
                entityManager.persist(parent);
                return parent.getId();
            }
        });
    }

}
