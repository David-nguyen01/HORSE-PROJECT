/* REVIEW ONLY. Compatibility rollback deliberately retains additive columns and
   populated lifecycle/assignment/injury/notes data. Deploy the prior application
   only after verifying its behavior against these retained columns.
   Restores the observed Azure intensity precision only when lossless.
   No tables, columns, ownerships, assignments, or evaluations are deleted. */
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.training_evaluations')
               AND name=N'intensity_percent' AND (precision<>5 OR scale<>1))
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.training_evaluations
                   WHERE intensity_percent <> ROUND(intensity_percent,1))
            THROW 51072, N'Rollback would lose intensity precision. Retain decimal(5,2) and review.', 1;
        ALTER TABLE dbo.training_evaluations ALTER COLUMN intensity_percent DECIMAL(5,1) NULL;
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
