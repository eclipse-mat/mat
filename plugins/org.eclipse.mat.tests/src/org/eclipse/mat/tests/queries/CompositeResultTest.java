package org.eclipse.mat.tests.queries;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.PropertyResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

@Category("Test")
public class CompositeResultTest implements IQuery
{
    @Argument(advice = Advice.HEAP_OBJECT)
    public int id[];

    @Argument
    public ISnapshot snapshot;
    
    public CompositeResultTest()
    {
    }

    public IResult execute(IProgressListener listener) throws Exception
    {
        PropertyResult pr = new PropertyResult(id);

        IResultTree it = new ObjectListResult.Outbound(snapshot, id);

        CompositeResult cr = new CompositeResult();
        cr.addResult("Properties", pr);
        cr.addResult("Object tree", it);

        cr.setAsHtml(true);
        cr.setName("Composite HTML with properties and object tree");
        
        Histogram h = snapshot.getHistogram(id, new VoidProgressListener());
        QuerySpec f = new QuerySpec("Query Spec for histogram", h);
        SectionSpec spec = new SectionSpec("Section spec");
        spec.setName("Section spec 2");
        spec.add(f);
        
        CompositeResult cr2 = new CompositeResult(spec, cr);
        cr2.setName("Composite Result 2");
        
        return cr2;
    }

}
