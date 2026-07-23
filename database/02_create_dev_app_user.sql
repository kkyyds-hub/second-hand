-- DEVELOPMENT ONLY: run as a MySQL administrator after importing 00_rebuild_secondhand2.sql.
-- Override before SOURCE when desired, for example:
--   SET @app_user = 'secondhand_dev';
--   SET @app_password = 'secondhand_dev_2026';

SET @app_user = COALESCE(@app_user, 'secondhand_dev');
SET @app_password = COALESCE(@app_password, 'secondhand_dev_2026');

SET @create_localhost = CONCAT(
  'CREATE USER IF NOT EXISTS ', QUOTE(@app_user), '@', QUOTE('localhost'),
  ' IDENTIFIED BY ', QUOTE(@app_password)
);
PREPARE create_localhost FROM @create_localhost;
EXECUTE create_localhost;
DEALLOCATE PREPARE create_localhost;

SET @create_loopback = CONCAT(
  'CREATE USER IF NOT EXISTS ', QUOTE(@app_user), '@', QUOTE('127.0.0.1'),
  ' IDENTIFIED BY ', QUOTE(@app_password)
);
PREPARE create_loopback FROM @create_loopback;
EXECUTE create_loopback;
DEALLOCATE PREPARE create_loopback;

SET @alter_localhost = CONCAT(
  'ALTER USER ', QUOTE(@app_user), '@', QUOTE('localhost'),
  ' IDENTIFIED BY ', QUOTE(@app_password)
);
PREPARE alter_localhost FROM @alter_localhost;
EXECUTE alter_localhost;
DEALLOCATE PREPARE alter_localhost;

SET @alter_loopback = CONCAT(
  'ALTER USER ', QUOTE(@app_user), '@', QUOTE('127.0.0.1'),
  ' IDENTIFIED BY ', QUOTE(@app_password)
);
PREPARE alter_loopback FROM @alter_loopback;
EXECUTE alter_loopback;
DEALLOCATE PREPARE alter_loopback;

SET @grant_localhost = CONCAT(
  'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, ',
  'CREATE TEMPORARY TABLES, LOCK TABLES ON `secondhand2`.* TO ',
  QUOTE(@app_user), '@', QUOTE('localhost')
);
PREPARE grant_localhost FROM @grant_localhost;
EXECUTE grant_localhost;
DEALLOCATE PREPARE grant_localhost;

SET @grant_loopback = CONCAT(
  'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, ',
  'CREATE TEMPORARY TABLES, LOCK TABLES ON `secondhand2`.* TO ',
  QUOTE(@app_user), '@', QUOTE('127.0.0.1')
);
PREPARE grant_loopback FROM @grant_loopback;
EXECUTE grant_loopback;
DEALLOCATE PREPARE grant_loopback;

FLUSH PRIVILEGES;
SELECT CONCAT('Development application account ready: ', @app_user) AS result;
