#ifndef _OSMAND_CORE_ROAD_LOCATOR_H_
#define _OSMAND_CORE_ROAD_LOCATOR_H_

#include <OsmAndCore/stdlib_common.h>

#include <OsmAndCore/QtExtensions.h>
#include <QList>

#include <OsmAndCore.h>
#include <OsmAndCore/CommonTypes.h>
#include <OsmAndCore/PrivateImplementation.h>
#include <OsmAndCore/IRoadLocator.h>
#include <OsmAndCore/Data/ObfRoutingSectionReader.h>

namespace OsmAnd
{
    class IObfsCollection;
    namespace Model
    {
        class Road;
    }

    class RoadLocator_P;
    class OSMAND_CORE_API RoadLocator : public IRoadLocator
    {
        Q_DISABLE_COPY(RoadLocator);
    private:
        PrivateImplementation<RoadLocator_P> _p;
    protected:
    public:
        RoadLocator(
            const std::shared_ptr<const IObfsCollection>& obfsCollection,
            const std::shared_ptr<ObfRoutingSectionReader::DataBlocksCache>& cache = nullptr);
        virtual ~RoadLocator();

        const std::shared_ptr<const IObfsCollection> obfsCollection;
        const std::shared_ptr<ObfRoutingSectionReader::DataBlocksCache> cache;

        virtual std::shared_ptr<const Model::Road> findNearestRoad(
            const PointI position31,
            const double radiusInMeters,
            const RoutingDataLevel dataLevel,
            int* const outNearestRoadPointIndex = nullptr,
            double* const outDistanceToNearestRoadPoint = nullptr) const;
        virtual QList< std::shared_ptr<const Model::Road> > findRoadsInArea(
            const PointI position31,
            const double radiusInMeters,
            const RoutingDataLevel dataLevel) const;

        std::shared_ptr<const Model::Road> findNearestRoadEx(
            const PointI position31,
            const double radiusInMeters,
            const RoutingDataLevel dataLevel,
            int* const outNearestRoadPointIndex = nullptr,
            double* const outDistanceToNearestRoadPoint = nullptr,
            QList< std::shared_ptr<const ObfRoutingSectionReader::DataBlock> >* const outReferencedCacheEntries = nullptr) const;
        QList< std::shared_ptr<const Model::Road> > findRoadsInAreaEx(
            const PointI position31,
            const double radiusInMeters,
            const RoutingDataLevel dataLevel,
            QList< std::shared_ptr<const ObfRoutingSectionReader::DataBlock> >* const outReferencedCacheEntries = nullptr) const;

        static std::shared_ptr<const Model::Road> findNearestRoad(
            const QList< std::shared_ptr<const Model::Road> >& collection,
            const PointI position31,
            const double radiusInMeters,
            int* const outNearestRoadPointIndex = nullptr,
            double* const outDistanceToNearestRoadPoint = nullptr);
        static std::shared_ptr<const Model::Road> findNearestRoad(
            const QList< std::shared_ptr<const Model::Road> >& collection,
            const PointI position31,
            int* const outNearestRoadPointIndex = nullptr,
            double* const outDistanceToNearestRoadPoint = nullptr);
        static QList< std::shared_ptr<const Model::Road> > findRoadsInArea(
            const QList< std::shared_ptr<const Model::Road> >& collection,
            const PointI position31,
            const double radiusInMeters);
    };
}

#endif // !defined(_OSMAND_CORE_ROAD_LOCATOR_H_)
