CREATE TABLE organizations (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)
-- statement
CREATE TABLE branches (id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL REFERENCES organizations(id),
            name TEXT NOT NULL, UNIQUE(organization_id,id))
-- statement
CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK(id=1), organization_id TEXT NOT NULL,
            branch_id TEXT NOT NULL, FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))
-- statement
CREATE TABLE subscribers (id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL, branch_id TEXT NOT NULL,
            name TEXT NOT NULL CHECK(length(name)>0), phone TEXT NOT NULL, service TEXT NOT NULL CHECK(service IN ('HOTSPOT','PPPOE','OTHER')),
            account TEXT NOT NULL, currency TEXT NOT NULL CHECK(currency IN ('EGP','USD','EUR','SAR','AED','TRY')),
            created_at INTEGER NOT NULL, UNIQUE(organization_id,branch_id,id),
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))
-- statement
CREATE INDEX subscriber_scope_name ON subscribers(organization_id,branch_id,name COLLATE NOCASE,id)
-- statement
CREATE TABLE ledger (sequence INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, subscriber_id TEXT NOT NULL,
            kind TEXT NOT NULL CHECK(kind IN ('CHARGE','PAYMENT','REVERSAL')),
            amount_minor INTEGER NOT NULL CHECK(typeof(amount_minor)='integer' AND amount_minor!=0 AND abs(amount_minor)<=999999999999),
            currency TEXT NOT NULL, note TEXT NOT NULL CHECK(length(note)>0), created_at INTEGER NOT NULL,
            reversal_of TEXT UNIQUE REFERENCES ledger(id),
            CHECK((kind='CHARGE' AND amount_minor>0 AND reversal_of IS NULL) OR
                  (kind='PAYMENT' AND amount_minor<0 AND reversal_of IS NULL) OR
                  (kind='REVERSAL' AND reversal_of IS NOT NULL)),
            FOREIGN KEY(organization_id,branch_id,subscriber_id) REFERENCES subscribers(organization_id,branch_id,id))
-- statement
CREATE INDEX ledger_scope_subscriber ON ledger(organization_id,branch_id,subscriber_id,sequence DESC)
-- statement
CREATE TRIGGER ledger_currency BEFORE INSERT ON ledger WHEN NOT EXISTS (
            SELECT 1 FROM subscribers WHERE id=NEW.subscriber_id AND organization_id=NEW.organization_id
            AND branch_id=NEW.branch_id AND currency=NEW.currency)
            BEGIN SELECT RAISE(ABORT,'CURRENCY_MISMATCH'); END
-- statement
CREATE TRIGGER ledger_reversal BEFORE INSERT ON ledger WHEN NEW.kind='REVERSAL' AND NOT EXISTS (
            SELECT 1 FROM ledger WHERE id=NEW.reversal_of AND kind IN ('CHARGE','PAYMENT')
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id AND subscriber_id=NEW.subscriber_id
            AND currency=NEW.currency AND amount_minor=-NEW.amount_minor)
            BEGIN SELECT RAISE(ABORT,'INVALID_REVERSAL'); END
-- statement
CREATE TRIGGER subscriber_ledger_currency BEFORE UPDATE OF currency ON subscribers
            WHEN OLD.currency!=NEW.currency AND EXISTS(SELECT 1 FROM ledger WHERE subscriber_id=OLD.id)
            BEGIN SELECT RAISE(ABORT,'IMMUTABLE_CURRENCY'); END
-- statement
CREATE TRIGGER ledger_no_update BEFORE UPDATE ON ledger BEGIN SELECT RAISE(ABORT,'IMMUTABLE_LEDGER'); END
-- statement
CREATE TRIGGER ledger_no_delete BEFORE DELETE ON ledger BEGIN SELECT RAISE(ABORT,'IMMUTABLE_LEDGER'); END
-- statement
INSERT INTO organizations VALUES('legacy-org','Existing business')
-- statement
INSERT INTO branches VALUES('legacy-branch','legacy-org','Main')
-- statement
INSERT INTO settings VALUES(1,'legacy-org','legacy-branch')
-- statement
INSERT INTO subscribers VALUES('legacy-sub','legacy-org','legacy-branch','Existing subscriber','','HOTSPOT','','EGP',1)
-- statement
INSERT INTO ledger(id,organization_id,branch_id,subscriber_id,kind,amount_minor,currency,note,created_at) VALUES('legacy-charge','legacy-org','legacy-branch','legacy-sub','CHARGE',700,'EGP','Old balance',1)